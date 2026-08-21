package com.lktransportes.service;

import com.lktransportes.dto.AceitarDemandaRequest;
import com.lktransportes.dto.NovaDemandaRequest;
import com.lktransportes.dto.TelemetriaPing;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A telemetria é o que o jogo diz — o único dado do sistema que o motorista não
 * digita. Dela saem a distância que paga a comissão, os eventos automáticos e os
 * sinais que a conferência usa.
 *
 * O agente manda um ping a cada 2 segundos. Estes testes montam essa sequência à
 * mão, porque é entre um ping e o seguinte que tudo acontece: o tanque que
 * encheu, o dano que subiu, o caminhão que saltou de lugar.
 */
@SpringBootTest
@ActiveProfiles("dev")
class TelemetriaTest {

    @Autowired TelemetriaService telemetria;
    @Autowired DemandaService demandas;
    @Autowired ViagemService viagens;
    @Autowired ViagemRepository viagemRepo;
    @Autowired UsuarioRepository usuarios;
    @Autowired CaminhaoRepository caminhoes;
    @Autowired TelemetriaViagemRepository telemetriaViagens;
    @Autowired CnhService cnhs;

    private Usuario motorista;
    private Caminhao caminhao;

    /**
     * Motorista novo a cada teste.
     *
     * A sessão de telemetria, o token e a viagem em andamento são todos por
     * motorista — e um motorista só tem uma viagem aberta por vez. Reaproveitar
     * o do seed faria o segundo teste esbarrar na viagem que o primeiro deixou
     * aberta, e a falha apareceria longe de onde nasceu.
     */
    @BeforeEach
    void motoristaNovo() {
        Usuario u = new Usuario();
        u.setNome("Teste telemetria");
        u.setEmail("tele-" + UUID.randomUUID() + "@lk.com");
        u.setSenhaHash("nao-usado");
        u.setPapel(Usuario.Papel.MOTORISTA);
        u.setStatusAcesso(Usuario.StatusAcesso.APROVADO);
        motorista = usuarios.save(u);

        Usuario gestor = usuarios.findByEmail("admin@lk.com").orElseThrow();
        cnhs.emitir(motorista.getId(), "E", null, gestor);

        caminhao = caminhoes.findAll().stream()
                .filter(c -> c.podeSerUsadoPor(motorista.getId()))
                .findFirst().orElseThrow();
    }

    // ----- token do agente -----

    @Test
    @DisplayName("o agente entra pelo token dele, não por JWT — e token errado é recusado")
    void tokenDoAgente() {
        String token = telemetria.gerarToken(motorista.getId());

        assertThat(telemetria.autenticar(token).getId()).isEqualTo(motorista.getId());
        assertThatThrownBy(() -> telemetria.autenticar("nao-e-um-token"))
                .isInstanceOf(TelemetriaService.TokenInvalidoException.class);
    }

    @Test
    @DisplayName("gerar token de novo derruba o pacote antigo")
    void gerarTokenInvalidaOAnterior() {
        String antigo = telemetria.gerarToken(motorista.getId());
        String novo = telemetria.gerarToken(motorista.getId());

        assertThat(novo).isNotEqualTo(antigo);
        assertThat(telemetria.autenticar(novo).getId()).isEqualTo(motorista.getId());
        assertThatThrownBy(() -> telemetria.autenticar(antigo))
                .describedAs("o agente já baixado para de funcionar, que é o objetivo")
                .isInstanceOf(TelemetriaService.TokenInvalidoException.class);
    }

    // ----- distância -----

    @Test
    @DisplayName("a distância sai do odômetro do jogo, não de palpite")
    void distanciaVemDoOdometro() {
        Viagem v = viagemEmAndamento();

        pingar(odometro(1000.0));
        pingar(odometro(1180.0));
        pingar(odometro(1512.0));

        assertThat(daViagem(v).getDistanciaConfirmadaKm()).isEqualTo(512.0);
    }

    @Test
    @DisplayName("um ping só não faz distância: sem trecho percorrido, não há o que confirmar")
    void umPingSoNaoFazDistancia() {
        Viagem v = viagemEmAndamento();

        pingar(odometro(1000.0));

        assertThat(daViagem(v).getDistanciaConfirmadaKm()).isEqualTo(0.0);
    }

    // ----- abastecimento automático -----

    @Test
    @DisplayName("o abastecimento fecha quando a bomba desliga, com os litros que entraram")
    void abastecimentoFechaAoDesligarABomba() {
        Viagem v = viagemEmAndamento();

        pingar(combustivel(120.0, false));
        pingar(combustivel(130.0, true));    // bomba ligada: guarda o nível inicial
        pingar(combustivel(300.0, true));    // enchendo — ainda não gera evento
        assertThat(abastecimentosDe(v)).describedAs("com a bomba ligada ainda não fechou").isEmpty();

        pingar(combustivel(520.0, false));   // desligou: fecha o evento

        List<Abastecimento> lancados = abastecimentosDe(v);
        assertThat(lancados).hasSize(1);
        assertThat(lancados.get(0).getLitros())
                .describedAs("520 − 130: o que entrou entre ligar e desligar a bomba")
                .isEqualByComparingTo(BigDecimal.valueOf(390));
        assertThat(lancados.get(0).getOrigem()).isEqualTo(EventoViagem.Origem.TELEMETRIA);
        assertThat(lancados.get(0).getValor())
                .describedAs("o jogo não expõe o preço por litro; entra sem custo para editar depois")
                .isNull();
    }

    @Test
    @DisplayName("respingo de combustível não vira abastecimento")
    void respingoNaoViraAbastecimento() {
        Viagem v = viagemEmAndamento();

        pingar(combustivel(120.0, true));
        pingar(combustivel(123.0, false));   // 3 litros, abaixo do mínimo

        assertThat(abastecimentosDe(v))
                .describedAs("abaixo do mínimo é ruído do jogo, não parada no posto")
                .isEmpty();
    }

    // ----- avaria automática -----

    @Test
    @DisplayName("dano subindo mais de 5 pontos vira ocorrência de avaria")
    void danoViraAvaria() {
        Viagem v = viagemEmAndamento();

        pingar(dano(2.0));
        assertThat(avariasDe(v)).describedAs("2% ainda é arranhão").isEmpty();

        pingar(dano(9.0));

        List<Ocorrencia> avarias = avariasDe(v);
        assertThat(avarias).hasSize(1);
        assertThat(avarias.get(0).getOrigem()).isEqualTo(EventoViagem.Origem.TELEMETRIA);
        assertThat(avarias.get(0).getDescricao()).contains("9");
    }

    @Test
    @DisplayName("o mesmo dano não vira ocorrência a cada ping")
    void danoNaoRepeteOEvento() {
        Viagem v = viagemEmAndamento();

        pingar(dano(0.0));    // primeiro ping fixa a linha de base
        pingar(dano(9.0));    // +9: avaria
        pingar(dano(9.0));    // não subiu
        pingar(dano(11.0));   // subiu só 2 desde o registrado

        assertThat(avariasDe(v))
                .describedAs("um ping a cada 2s geraria dezenas de ocorrências do mesmo amasso")
                .hasSize(1);
    }

    @Test
    @DisplayName("dano que o caminhão já tinha ao sair não é avaria da viagem")
    void danoAnteriorNaoContaContraOMotorista() {
        Viagem v = viagemEmAndamento();

        // Sai do pátio com o caminhão já amassado de 30%.
        pingar(dano(30.0));
        pingar(dano(30.0));
        pingar(dano(33.0));   // +3 na viagem: abaixo do mínimo

        assertThat(avariasDe(v))
                .describedAs("o primeiro ping é a linha de base; a viagem só responde pelo que ela causou")
                .isEmpty();
        assertThat(daViagem(v).getDanoInicialPct()).isEqualTo(30.0);

        pingar(dano(40.0));   // +10 desde o registrado: agora sim
        assertThat(avariasDe(v)).hasSize(1);
    }

    // ----- sinais para a conferência -----

    @Test
    @DisplayName("salto de posição é contado — teleporte ou reboque no meio da viagem")
    void saltoDePosicaoEContado() {
        Viagem v = viagemEmAndamento();

        pingar(posicao(0.0, 0.0));
        pingar(posicao(300.0, 0.0));        // 300 m: dirigindo
        assertThat(daViagem(v).getSaltos()).isZero();

        pingar(posicao(90_000.0, 0.0));     // 89,7 km num piscar: teleporte

        assertThat(daViagem(v).getSaltos()).isEqualTo(1);
    }

    @Test
    @DisplayName("piloto e estacionamento automáticos ficam registrados, mas não retêm a viagem")
    void automaticosFicamRegistradosSemReter() {
        Viagem v = viagemEmAndamento();

        TelemetriaPing p = base();
        p.odometroKm = 1000.0;
        p.pilotoAutomatico = true;
        p.estacionamentoAutomatico = true;
        pingar(p);
        pingar(odometro(1400.0));

        TelemetriaViagem t = daViagem(v);
        assertThat(t.getUsouPilotoAutomatico()).isTrue();
        assertThat(t.getUsouEstacionamentoAutomatico()).isTrue();

        viagens.finalizar(v.getId(), "entregue", false);
        assertThat(viagemRepo.findById(v.getId()).orElseThrow().getConferencia())
                .describedAs("é informação para o gestor olhar, não motivo de retenção")
                .isEqualTo(Viagem.Conferencia.APROVADA);
    }

    // ----- ping fora de viagem -----

    @Test
    @DisplayName("ping com o jogo aberto e nenhuma viagem em andamento não quebra nada")
    void pingSemViagemEmAndamento() {
        // Sem viagem aberta, o ping só atualiza o painel ao vivo.
        telemetria.registrar(motorista, odometro(500.0));

        assertThat(telemetria.sessaoDe(motorista.getId())).isPresent();
        assertThat(telemetria.numeroDaViagemAtiva(motorista.getId())).isNull();
    }

    // ----- apoio -----

    private Viagem viagemEmAndamento() {
        NovaDemandaRequest req = new NovaDemandaRequest();
        req.origem = "Sinop";
        req.destino = "Cuiabá";
        req.empresaRemetente = "Agro Sinop Cereais";
        req.empresaDestinataria = "Frigorífico Vale Verde";
        req.carga = "Soja";
        req.quantidadeTotalKg = BigDecimal.valueOf(50_000);
        req.fretePorTonelada = BigDecimal.valueOf(150);
        UUID demandaId = demandas.criar(req).id();

        AceitarDemandaRequest aceite = new AceitarDemandaRequest();
        aceite.pesoKg = BigDecimal.valueOf(25_000);
        aceite.caminhaoId = caminhao.getId();

        UUID id = demandas.aceitar(demandaId, motorista.getId(), aceite).id();
        viagens.iniciar(id);
        return viagemRepo.findById(id).orElseThrow();
    }

    private void pingar(TelemetriaPing p) {
        telemetria.registrar(motorista, p);
    }

    private TelemetriaViagem daViagem(Viagem v) {
        return telemetriaViagens.findByViagemId(v.getId()).orElseThrow();
    }

    private List<EventoViagem> eventosDe(Viagem v) {
        return viagemRepo.findWithEventosById(v.getId()).orElseThrow().getEventos();
    }

    private List<Abastecimento> abastecimentosDe(Viagem v) {
        return eventosDe(v).stream()
                .filter(Abastecimento.class::isInstance).map(Abastecimento.class::cast).toList();
    }

    private List<Ocorrencia> avariasDe(Viagem v) {
        return eventosDe(v).stream()
                .filter(Ocorrencia.class::isInstance).map(Ocorrencia.class::cast)
                .filter(o -> o.getOrigem() == EventoViagem.Origem.TELEMETRIA).toList();
    }

    /** Ping mínimo com a carga batendo, para o teste isolar só o que quer medir. */
    private TelemetriaPing base() {
        TelemetriaPing p = new TelemetriaPing();
        p.jogoAtivo = true;
        p.cargaNome = "Soja";
        p.cargaMassaKg = 25_000.0;
        return p;
    }

    private TelemetriaPing odometro(double km) {
        TelemetriaPing p = base();
        p.odometroKm = km;
        return p;
    }

    private TelemetriaPing combustivel(double litros, boolean abastecendo) {
        TelemetriaPing p = base();
        p.odometroKm = 1000.0;
        p.combustivelL = litros;
        p.abastecendo = abastecendo;
        return p;
    }

    private TelemetriaPing dano(double pct) {
        TelemetriaPing p = base();
        p.odometroKm = 1000.0;
        p.desgasteMotorPct = pct;
        return p;
    }

    private TelemetriaPing posicao(double x, double z) {
        TelemetriaPing p = base();
        p.odometroKm = 1000.0;
        p.posX = x;
        p.posZ = z;
        return p;
    }
}
