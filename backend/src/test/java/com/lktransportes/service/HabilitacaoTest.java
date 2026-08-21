package com.lktransportes.service;

import com.lktransportes.dto.AceitarDemandaRequest;
import com.lktransportes.dto.NovaDemandaRequest;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A CNH é o freio da operação: sem carteira válida o motorista não pega carga.
 * É esse bloqueio que faz a renovação importar — e é ele que some sem avisar se
 * alguém mexer no `valida()`.
 *
 * Cada teste usa um motorista próprio: o bloqueio mexe no estado do usuário, e
 * um teste vencendo a carteira do seed derrubaria todos os outros.
 */
@SpringBootTest
@ActiveProfiles("dev")
class HabilitacaoTest {

    @Autowired CnhService cnhs;
    @Autowired CnhRepository cnhRepo;
    @Autowired UsuarioRepository usuarios;
    @Autowired CaminhaoRepository caminhoes;
    @Autowired DemandaService demandas;
    @Autowired ViagemService viagens;
    @Autowired ViagemRepository viagemRepo;
    @Autowired EventoViagemRepository eventos;

    private Usuario gestor;

    @BeforeEach
    void carregarSeed() {
        gestor = usuarios.findByEmail("admin@lk.com").orElseThrow();
    }

    // ----- emissão e prazo -----

    @Test
    @DisplayName("emitir dá 3 meses de prazo e a pontuação cheia")
    void emitirDaPrazoEPontos() {
        Usuario m = novoMotorista("emissao");
        Cnh c = cnhs.emitir(m.getId(), "E", null, gestor);

        assertThat(c.getPontos()).isEqualTo(Cnh.PONTOS_INICIAIS);
        assertThat(c.getValidade()).isEqualTo(LocalDate.now().plusMonths(CnhService.MESES_DE_VALIDADE));
        assertThat(c.valida()).isTrue();
        assertThat(c.estado()).isEqualTo("ATIVA");
        assertThat(c.getEmitidaPor().getId()).isEqualTo(gestor.getId());
    }

    @Test
    @DisplayName("CNH vencida bloqueia, e a mensagem diz a data")
    void vencidaBloqueia() {
        Usuario m = novoMotorista("vencida");
        cnhs.emitir(m.getId(), "E", LocalDate.now().minusDays(1), gestor);

        Cnh c = cnhRepo.findByMotoristaId(m.getId()).orElseThrow();
        assertThat(c.vencida()).isTrue();
        assertThat(c.valida()).isFalse();
        assertThat(c.estado()).isEqualTo("VENCIDA");

        assertThatThrownBy(() -> cnhs.exigirCnhValida(m.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CNH vencida em");
    }

    @Test
    @DisplayName("motorista sem CNH nenhuma não pega carga")
    void semCnhNaoPegaCarga() {
        Usuario m = novoMotorista("sem-cnh");

        assertThatThrownBy(() -> cnhs.exigirCnhValida(m.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ainda não tem CNH emitida");
    }

    // ----- pontos -----

    @Test
    @DisplayName("multa tira 5 pontos e avaria da telemetria tira 3")
    void multaEAvariaDescontamPontos() {
        Usuario m = novoMotorista("pontos");
        cnhs.emitir(m.getId(), "E", null, gestor);

        Viagem v = rodarViagemCom(m, 1, 1);   // uma multa, uma avaria
        int perdidos = CnhService.PONTOS_POR_MULTA + CnhService.PONTOS_POR_AVARIA;

        assertThat(cnhRepo.findByMotoristaId(m.getId()).orElseThrow().getPontos())
                .isEqualTo(Cnh.PONTOS_INICIAIS - perdidos);
        assertThat(v.getStatus()).isEqualTo(StatusViagem.CONCLUIDA);
    }

    @Test
    @DisplayName("ocorrência lançada à mão não tira ponto — só a avaria que a telemetria detectou")
    void ocorrenciaManualNaoTiraPonto() {
        Usuario m = novoMotorista("ocorrencia-manual");
        cnhs.emitir(m.getId(), "E", null, gestor);

        rodarViagemCom(m, 0, 0, true);   // ocorrência de origem MANUAL

        assertThat(cnhRepo.findByMotoristaId(m.getId()).orElseThrow().getPontos())
                .describedAs("o motorista relatar um problema não pode custar a carteira dele")
                .isEqualTo(Cnh.PONTOS_INICIAIS);
    }

    @Test
    @DisplayName("zerar os pontos suspende a carteira sozinha e trava o motorista")
    void pontosZeradosSuspendem() {
        Usuario m = novoMotorista("zerado");
        cnhs.emitir(m.getId(), "E", null, gestor);

        // 4 multas = 20 pontos = a pontuação inteira.
        rodarViagemCom(m, 4, 0);

        Cnh c = cnhRepo.findByMotoristaId(m.getId()).orElseThrow();
        assertThat(c.getPontos()).isZero();
        assertThat(c.getSituacao()).isEqualTo(Cnh.Situacao.SUSPENSA);
        assertThat(c.estado()).isEqualTo("SUSPENSA");

        assertThatThrownBy(() -> cnhs.exigirCnhValida(m.getId()))
                .hasMessageContaining("pontuação zerada");
    }

    @Test
    @DisplayName("o desconto não passa de zero: 100 pontos de multa não devem 80")
    void descontoNaoFicaNegativo() {
        Usuario m = novoMotorista("nao-negativo");
        cnhs.emitir(m.getId(), "E", null, gestor);

        rodarViagemCom(m, 20, 0);   // 100 pontos de multa

        assertThat(cnhRepo.findByMotoristaId(m.getId()).orElseThrow().getPontos()).isZero();
    }

    // ----- decisão do gestor -----

    @Test
    @DisplayName("suspender é decisão manual, e fica registrada")
    void suspenderRegistra() {
        Usuario m = novoMotorista("suspenso");
        cnhs.emitir(m.getId(), "E", null, gestor);

        cnhs.suspender(m.getId(), gestor, "dirigiu de madrugada sem avisar");

        Cnh c = cnhRepo.findByMotoristaId(m.getId()).orElseThrow();
        assertThat(c.getSituacao()).isEqualTo(Cnh.Situacao.SUSPENSA);
        assertThat(c.getObservacoes()).contains("madrugada");
        assertThat(c.getPontos())
                .describedAs("suspender pela gestão não mexe na pontuação")
                .isEqualTo(Cnh.PONTOS_INICIAIS);

        assertThatThrownBy(() -> cnhs.exigirCnhValida(m.getId()))
                .hasMessageContaining("suspensa pela gestão");
    }

    @Test
    @DisplayName("devolver pontos reabilita sem mexer no prazo")
    void reabilitarDevolvePontosESoIsso() {
        Usuario m = novoMotorista("reabilitado");
        LocalDate prazo = LocalDate.now().plusDays(10);
        cnhs.emitir(m.getId(), "E", prazo, gestor);
        rodarViagemCom(m, 4, 0);   // zera e suspende

        cnhs.reabilitar(m.getId(), gestor, "conversamos");

        Cnh c = cnhRepo.findByMotoristaId(m.getId()).orElseThrow();
        assertThat(c.getPontos()).isEqualTo(Cnh.PONTOS_INICIAIS);
        assertThat(c.getSituacao()).isEqualTo(Cnh.Situacao.ATIVA);
        assertThat(c.getValidade())
                .describedAs("devolver pontos não é renovar: o prazo continua o mesmo")
                .isEqualTo(prazo);
    }

    @Test
    @DisplayName("renovar devolve prazo e pontos de uma vez")
    void renovarDevolveTudo() {
        Usuario m = novoMotorista("renovado");
        // Precisa estar válida para conseguir rodar e perder pontos — vencida,
        // o motorista nem pega a carga. O vencimento vem depois, à mão.
        cnhs.emitir(m.getId(), "E", null, gestor);
        rodarViagemCom(m, 2, 0);

        Cnh gasta = cnhRepo.findByMotoristaId(m.getId()).orElseThrow();
        gasta.setValidade(LocalDate.now().minusDays(1));
        cnhRepo.save(gasta);
        assertThat(gasta.valida()).isFalse();

        cnhs.emitir(m.getId(), "E", null, gestor);   // emitir de novo = renovar

        Cnh c = cnhRepo.findByMotoristaId(m.getId()).orElseThrow();
        assertThat(c.getPontos()).isEqualTo(Cnh.PONTOS_INICIAIS);
        assertThat(c.vencida()).isFalse();
        assertThat(c.valida()).isTrue();
        cnhs.exigirCnhValida(m.getId());   // não lança: voltou a rodar
    }

    // ----- o bloqueio na porta de entrada -----

    @Test
    @DisplayName("sem CNH válida a demanda é recusada — é isso que faz a renovação importar")
    void semCnhValidaNaoAceitaDemanda() {
        Usuario m = novoMotorista("bloqueado");
        cnhs.emitir(m.getId(), "E", LocalDate.now().minusDays(1), gestor);

        UUID demandaId = criarDemanda(BigDecimal.valueOf(50_000));
        AceitarDemandaRequest req = new AceitarDemandaRequest();
        req.pesoKg = BigDecimal.valueOf(20_000);
        req.caminhaoId = caminhaoLivre(m).getId();

        assertThatThrownBy(() -> demandas.aceitar(demandaId, m.getId(), req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CNH vencida em");
    }

    // ----- apoio -----

    private Usuario novoMotorista(String apelido) {
        Usuario u = new Usuario();
        u.setNome("Teste " + apelido);
        u.setEmail(apelido + "-" + UUID.randomUUID() + "@lk.com");
        u.setSenhaHash("nao-usado");
        u.setPapel(Usuario.Papel.MOTORISTA);
        u.setStatusAcesso(Usuario.StatusAcesso.APROVADO);
        return usuarios.save(u);
    }

    private Caminhao caminhaoLivre(Usuario m) {
        return caminhoes.findAll().stream()
                .filter(c -> c.podeSerUsadoPor(m.getId()))
                .findFirst().orElseThrow();
    }

    private UUID criarDemanda(BigDecimal totalKg) {
        NovaDemandaRequest req = new NovaDemandaRequest();
        req.origem = "Sinop";
        req.destino = "Cuiabá";
        req.empresaRemetente = "Agro Sinop Cereais";
        req.empresaDestinataria = "Frigorífico Vale Verde";
        req.carga = "Soja";
        req.quantidadeTotalKg = totalKg;
        req.fretePorTonelada = BigDecimal.valueOf(150);
        return demandas.criar(req).id();
    }

    private Viagem rodarViagemCom(Usuario m, int multas, int avarias) {
        return rodarViagemCom(m, multas, avarias, false);
    }

    /** Roda uma viagem inteira pendurando nela as multas e avarias pedidas. */
    private Viagem rodarViagemCom(Usuario m, int multas, int avarias, boolean ocorrenciaManual) {
        UUID demandaId = criarDemanda(BigDecimal.valueOf(50_000));

        AceitarDemandaRequest req = new AceitarDemandaRequest();
        req.pesoKg = BigDecimal.valueOf(20_000);
        req.caminhaoId = caminhaoLivre(m).getId();

        UUID id = demandas.aceitar(demandaId, m.getId(), req).id();
        viagens.iniciar(id);

        Viagem v = viagemRepo.findById(id).orElseThrow();
        for (int i = 0; i < multas; i++) {
            Multa multa = new Multa();
            multa.setViagem(v);
            multa.setMotivo("excesso de velocidade");
            multa.setValor(BigDecimal.valueOf(195.23));
            eventos.save(multa);
        }
        for (int i = 0; i < avarias; i++) {
            eventos.save(ocorrencia(v, EventoViagem.Origem.TELEMETRIA, "Avaria detectada"));
        }
        if (ocorrenciaManual) {
            eventos.save(ocorrencia(v, EventoViagem.Origem.MANUAL, "Estrada interditada"));
        }

        viagens.finalizar(id, "entregue", false);
        return viagemRepo.findById(id).orElseThrow();
    }

    private Ocorrencia ocorrencia(Viagem v, EventoViagem.Origem origem, String titulo) {
        Ocorrencia o = new Ocorrencia();
        o.setViagem(v);
        o.setTitulo(titulo);
        o.setOrigem(origem);
        return o;
    }
}
