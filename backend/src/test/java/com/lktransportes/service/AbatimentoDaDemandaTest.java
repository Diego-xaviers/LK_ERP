package com.lktransportes.service;

import com.lktransportes.dto.AceitarDemandaRequest;
import com.lktransportes.dto.NovaDemandaRequest;
import com.lktransportes.model.Caminhao;
import com.lktransportes.model.Demanda;
import com.lktransportes.model.Usuario;
import com.lktransportes.model.Viagem;
import com.lktransportes.repository.CaminhaoRepository;
import com.lktransportes.repository.DemandaRepository;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.repository.ViagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra mais fácil de quebrar sem ninguém perceber: uma entrega só abate a
 * demanda quando a viagem passa pela conferência.
 *
 * Se isso regredir, dá para fechar um contrato inteiro no papel com viagens que
 * o jogo nunca confirmou — e não aparece erro nenhum na tela, só um número
 * errado na barra de progresso.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AbatimentoDaDemandaTest {

    @Autowired DemandaService demandas;
    @Autowired ViagemService viagens;
    @Autowired DemandaRepository demandaRepo;
    @Autowired ViagemRepository viagemRepo;
    @Autowired UsuarioRepository usuarios;
    @Autowired CaminhaoRepository caminhoes;

    private Usuario motorista;
    private Usuario gestor;
    private Caminhao caminhao;

    @BeforeEach
    void carregarSeed() {
        // O seed de desenvolvimento já cria os dois usuários com CNH válida —
        // sem carteira o motorista nem pega carga, e o teste morreria na largada.
        motorista = usuarios.findByEmail("motorista@lk.com").orElseThrow();
        gestor = usuarios.findByEmail("admin@lk.com").orElseThrow();
        caminhao = caminhoes.findAll().stream()
                .filter(c -> c.podeSerUsadoPor(motorista.getId()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("viagem retida não abate a demanda; abate quando o gestor libera")
    void retidaSoAbateDepoisDeLiberada() {
        Demanda d = criarDemanda(BigDecimal.valueOf(100_000));
        Viagem v = rodarViagem(d, BigDecimal.valueOf(30_000));

        // Sem telemetria a conferência retém — é o cenário do dia a dia de quem
        // esquece de abrir o agente.
        assertThat(v.getConferencia()).isEqualTo(Viagem.Conferencia.RETIDA);

        Demanda apos = demandaRepo.findById(d.getId()).orElseThrow();
        assertThat(apos.getQuantidadeEntregueKg())
                .describedAs("retida não pode abater")
                .isEqualByComparingTo(BigDecimal.ZERO);

        // Enquanto espera decisão, a carga continua comprometida: se saísse das
        // duas contas, outro motorista pegaria o mesmo saldo.
        assertThat(viagemRepo.pesoEmCursoDaDemanda(d.getId()))
                .describedAs("retida continua reservada")
                .isEqualByComparingTo(BigDecimal.valueOf(30_000));

        viagens.liberar(v.getId(), gestor, "conferido à mão");

        Demanda liberada = demandaRepo.findById(d.getId()).orElseThrow();
        assertThat(liberada.getQuantidadeEntregueKg()).isEqualByComparingTo(BigDecimal.valueOf(30_000));
        assertThat(viagemRepo.pesoEmCursoDaDemanda(d.getId()))
                .describedAs("liberada vira entrega e solta a reserva")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("liberar duas vezes não abate a demanda em dobro")
    void liberarDuasVezesNaoAbateEmDobro() {
        Demanda d = criarDemanda(BigDecimal.valueOf(100_000));
        Viagem v = rodarViagem(d, BigDecimal.valueOf(30_000));

        viagens.liberar(v.getId(), gestor, "primeira");
        try {
            viagens.liberar(v.getId(), gestor, "segunda");
        } catch (IllegalStateException esperado) {
            // liberarConferencia() recusa viagem que não está retida — é essa
            // recusa que garante o abatimento uma vez só.
        }

        assertThat(demandaRepo.findById(d.getId()).orElseThrow().getQuantidadeEntregueKg())
                .isEqualByComparingTo(BigDecimal.valueOf(30_000));
    }

    @Test
    @DisplayName("a demanda continua da transportadora: várias viagens abatem o mesmo contrato")
    void variasViagensAbatemAMesmaDemanda() {
        Demanda d = criarDemanda(BigDecimal.valueOf(100_000));

        for (int i = 0; i < 3; i++) {
            Viagem v = rodarViagem(d, BigDecimal.valueOf(20_000));
            viagens.liberar(v.getId(), gestor, "conferido");
        }

        Demanda apos = demandaRepo.findById(d.getId()).orElseThrow();
        assertThat(apos.getQuantidadeEntregueKg()).isEqualByComparingTo(BigDecimal.valueOf(60_000));
        assertThat(apos.getStatus())
                .describedAs("ainda falta entregar, então não fecha")
                .isEqualTo(Demanda.Status.ABERTA);
        assertThat(apos.saldoKg()).isEqualByComparingTo(BigDecimal.valueOf(40_000));
    }

    @Test
    @DisplayName("a demanda fecha sozinha quando a quantidade contratada se completa")
    void fechaAoCompletarAQuantidade() {
        Demanda d = criarDemanda(BigDecimal.valueOf(50_000));

        Viagem v = rodarViagem(d, BigDecimal.valueOf(50_000));
        viagens.liberar(v.getId(), gestor, "conferido");

        assertThat(demandaRepo.findById(d.getId()).orElseThrow().getStatus())
                .isEqualTo(Demanda.Status.CONCLUIDA);
    }

    // ----- apoio -----

    private Demanda criarDemanda(BigDecimal totalKg) {
        NovaDemandaRequest req = new NovaDemandaRequest();
        req.origem = "Sinop";
        req.destino = "Cuiabá";
        req.empresaRemetente = "Agro Sinop Cereais";
        req.empresaDestinataria = "Frigorífico Vale Verde";
        req.carga = "Soja";
        req.quantidadeTotalKg = totalKg;
        req.fretePorTonelada = BigDecimal.valueOf(180);
        return demandaRepo.findById(demandas.criar(req).id()).orElseThrow();
    }

    /** Pega a carga, sai e entrega — sem telemetria, como quem esquece o agente. */
    private Viagem rodarViagem(Demanda d, BigDecimal pesoKg) {
        AceitarDemandaRequest req = new AceitarDemandaRequest();
        req.pesoKg = pesoKg;
        req.caminhaoId = caminhao.getId();

        UUID id = demandas.aceitar(d.getId(), motorista.getId(), req).id();
        viagens.iniciar(id);
        viagens.finalizar(id, "entregue", false);
        return viagemRepo.findById(id).orElseThrow();
    }
}
