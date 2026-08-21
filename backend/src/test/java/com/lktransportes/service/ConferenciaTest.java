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
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A conferência é o antifraude do sistema: ela compara o que o motorista
 * declarou com o que o jogo reportou, e segura a viagem quando os dois não
 * batem.
 *
 * É a parte mais fácil de afrouxar sem perceber — basta alguém "consertar" um
 * null check e a viagem sem telemetria passa a ser aprovada. Estes testes
 * existem para essa mudança quebrar o build em vez de quebrar o jogo.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ConferenciaTest {

    @Autowired DemandaService demandas;
    @Autowired ViagemService viagens;
    @Autowired ViagemRepository viagemRepo;
    @Autowired UsuarioRepository usuarios;
    @Autowired CaminhaoRepository caminhoes;
    @Autowired TelemetriaViagemRepository telemetrias;

    private Usuario motorista;
    private Caminhao caminhao;

    @BeforeEach
    void carregarSeed() {
        motorista = usuarios.findByEmail("motorista@lk.com").orElseThrow();
        caminhao = caminhoes.findAll().stream()
                .filter(c -> c.podeSerUsadoPor(motorista.getId()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("telemetria batendo com o declarado: viagem aprovada")
    void telemetriaBatendoAprova() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(512.0);
            t.setCargaJogo("Soja");
            t.setPesoJogoKg(25_000.0);
        });

        assertThat(v.getConferencia()).isEqualTo(Viagem.Conferencia.APROVADA);
        assertThat(v.getMotivosConferencia()).isNull();
    }

    @Test
    @DisplayName("sem telemetria nenhuma: retida — desligar o agente seria o jeito fácil de burlar")
    void semTelemetriaRetem() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), null);

        assertThat(v.getConferencia()).isEqualTo(Viagem.Conferencia.RETIDA);
        assertThat(v.getMotivosConferencia()).contains("não teve telemetria");
    }

    @Test
    @DisplayName("agente ligado mas caminhão parado: retida por falta de distância")
    void semDistanciaRetem() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(0.4);       // abaixo do mínimo de 1 km
            t.setCargaJogo("Soja");
            t.setPesoJogoKg(25_000.0);
        });

        assertThat(v.getConferencia()).isEqualTo(Viagem.Conferencia.RETIDA);
        assertThat(v.getMotivosConferencia()).contains("não confirmou distância");
    }

    @Test
    @DisplayName("carga diferente da que o jogo reportou: retida")
    void cargaDivergenteRetem() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(512.0);
            t.setCargaJogo("Cimento");
            t.setPesoJogoKg(25_000.0);
        });

        assertThat(v.getConferencia()).isEqualTo(Viagem.Conferencia.RETIDA);
        assertThat(v.getMotivosConferencia()).contains("Cimento");
    }

    @Test
    @DisplayName("acento e caixa não fazem a carga divergir")
    void cargaComparaIgnorandoAcentoECaixa() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(512.0);
            t.setCargaJogo("SOJA");      // declarada como "Soja"
            t.setPesoJogoKg(25_000.0);
        });

        assertThat(v.getConferencia())
                .describedAs("o jogo escreve diferente; isso não é fraude")
                .isEqualTo(Viagem.Conferencia.APROVADA);
    }

    @Test
    @DisplayName("peso dentro dos 5% passa; fora deles, retém")
    void pesoForaDaToleranciaRetem() {
        // 4% a menos: arredondamento do jogo, não mentira.
        Viagem dentro = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(512.0);
            t.setCargaJogo("Soja");
            t.setPesoJogoKg(24_000.0);
        });
        assertThat(dentro.getConferencia()).isEqualTo(Viagem.Conferencia.APROVADA);

        // 20% a menos: declarou carga cheia e levou meia.
        Viagem fora = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(512.0);
            t.setCargaJogo("Soja");
            t.setPesoJogoKg(20_000.0);
        });
        assertThat(fora.getConferencia()).isEqualTo(Viagem.Conferencia.RETIDA);
        assertThat(fora.getMotivosConferencia()).contains("Peso declarado");
    }

    @Test
    @DisplayName("salto de posição — teleporte ou reboque — retém")
    void saltoDePosicaoRetem() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(512.0);
            t.setCargaJogo("Soja");
            t.setPesoJogoKg(25_000.0);
            t.setSaltos(2);
        });

        assertThat(v.getConferencia()).isEqualTo(Viagem.Conferencia.RETIDA);
        assertThat(v.getMotivosConferencia()).contains("salto");
    }

    @Test
    @DisplayName("a viagem retida guarda todos os motivos, não só o primeiro")
    void juntaTodosOsMotivos() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), t -> {
            t.setDistanciaConfirmadaKm(0.2);
            t.setCargaJogo("Cimento");
            t.setPesoJogoKg(9_000.0);
        });

        String motivos = v.getMotivosConferencia();
        assertThat(motivos).contains("distância");
        assertThat(motivos).contains("Cimento");
        assertThat(motivos).contains("Peso declarado");
    }

    @Test
    @DisplayName("liberar tira a retenção, e liberar de novo é recusado")
    void liberarUmaVezSo() {
        Viagem v = rodar(BigDecimal.valueOf(25_000), null);
        Usuario gestor = usuarios.findByEmail("admin@lk.com").orElseThrow();

        viagens.liberar(v.getId(), gestor, "assumo a viagem");

        Viagem liberada = viagemRepo.findById(v.getId()).orElseThrow();
        assertThat(liberada.getConferencia()).isEqualTo(Viagem.Conferencia.LIBERADA);
        assertThat(liberada.liberadaParaPagamento()).isTrue();
        assertThat(liberada.getLiberadaPor().getId()).isEqualTo(gestor.getId());
        assertThat(liberada.getObservacaoLiberacao()).isEqualTo("assumo a viagem");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> viagens.liberar(v.getId(), gestor, "de novo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não está retida");
    }

    // ----- apoio -----

    /** Roda a viagem inteira, deixando o teste preencher a telemetria antes da chegada. */
    private Viagem rodar(BigDecimal pesoKg, Consumer<TelemetriaViagem> comoOJogoViu) {
        NovaDemandaRequest req = new NovaDemandaRequest();
        req.origem = "Sinop";
        req.destino = "Cuiabá";
        req.empresaRemetente = "Agro Sinop Cereais";
        req.empresaDestinataria = "Frigorífico Vale Verde";
        req.carga = "Soja";
        req.quantidadeTotalKg = pesoKg;
        req.fretePorTonelada = BigDecimal.valueOf(150);
        UUID demandaId = demandas.criar(req).id();

        AceitarDemandaRequest aceite = new AceitarDemandaRequest();
        aceite.pesoKg = pesoKg;
        aceite.caminhaoId = caminhao.getId();

        UUID id = demandas.aceitar(demandaId, motorista.getId(), aceite).id();
        viagens.iniciar(id);

        if (comoOJogoViu != null) {
            TelemetriaViagem t = new TelemetriaViagem();
            t.setViagem(viagemRepo.findById(id).orElseThrow());
            comoOJogoViu.accept(t);
            telemetrias.save(t);
        }

        viagens.finalizar(id, "entregue", false);
        return viagemRepo.findById(id).orElseThrow();
    }
}
