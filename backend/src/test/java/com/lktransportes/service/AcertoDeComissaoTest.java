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
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O acerto é onde o dinheiro sai do caixa e entra na carteira do motorista.
 * Um erro aqui não aparece na tela — aparece no saldo, dias depois.
 *
 * Estes testes cobrem as amarras que não dependem da fórmula da comissão:
 * quem entra no acerto, quantas vezes se paga, e o que o caixa recusa.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AcertoDeComissaoTest {

    @Autowired FinanceiroService financeiro;
    @Autowired DemandaService demandas;
    @Autowired ViagemService viagens;
    @Autowired ViagemRepository viagemRepo;
    @Autowired UsuarioRepository usuarios;
    @Autowired CaminhaoRepository caminhoes;
    @Autowired MovimentoCarteiraRepository movimentosCarteira;
    @Autowired PagamentoRepository pagamentos;
    @Autowired TelemetriaViagemRepository telemetrias;

    private Usuario motorista;
    private Usuario gestor;
    private Caminhao caminhao;

    @BeforeEach
    void carregarSeed() {
        motorista = usuarios.findByEmail("motorista@lk.com").orElseThrow();
        gestor = usuarios.findByEmail("admin@lk.com").orElseThrow();
        caminhao = caminhoes.findAll().stream()
                .filter(c -> c.podeSerUsadoPor(motorista.getId()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("viagem retida não entra no acerto até o gestor liberar")
    void retidaNaoEntraNoAcerto() {
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000));

        assertThat(financeiro.pagaveis(motorista.getId()))
                .describedAs("retida não é pagável")
                .noneMatch(p -> p.getId().equals(v.getId()));

        viagens.liberar(v.getId(), gestor, "conferido");

        assertThat(financeiro.pagaveis(motorista.getId()))
                .describedAs("liberada entra na fila de acerto")
                .anyMatch(p -> p.getId().equals(v.getId()));
    }

    @Test
    @DisplayName("a mesma viagem não é paga duas vezes")
    void naoPagaAMesmaViagemDuasVezes() {
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000), 480.0);
        deixarPagavel(v);

        Pagamento primeiro = financeiro.pagar(motorista.getId(), List.of(v.getId()), "1o acerto", gestor);
        assertThat(primeiro.getValor()).isGreaterThan(BigDecimal.ZERO);

        // A viagem fica amarrada ao pagamento; sem outra pagável, não há o que acertar.
        assertThatThrownBy(() -> financeiro.pagar(motorista.getId(), List.of(v.getId()), "2o acerto", gestor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Não há viagem liberada para acertar");

        assertThat(viagemRepo.findById(v.getId()).orElseThrow().getPagamento().getId())
                .isEqualTo(primeiro.getId());
    }

    @Test
    @DisplayName("o caixa não fica negativo: acerto maior que o saldo é recusado")
    void caixaNaoFicaNegativo() {
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000), 480.0);
        deixarPagavel(v);

        // Esvazia o caixa por fora, como uma retirada do dono.
        BigDecimal saldo = financeiro.caixa().getSaldo();
        financeiro.ajustar(saldo.negate(), "retirada", gestor);
        assertThat(financeiro.caixa().getSaldo()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThatThrownBy(() -> financeiro.pagar(motorista.getId(), null, "sem caixa", gestor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo insuficiente");

        assertThat(financeiro.caixa().getSaldo())
                .describedAs("recusa não pode deixar rastro no saldo")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(viagemRepo.findById(v.getId()).orElseThrow().getPagamento())
                .describedAs("viagem não pode ficar marcada como paga")
                .isNull();
    }

    @Test
    @DisplayName("o acerto sai do caixa e entra na carteira do motorista")
    void acertoCreditaACarteira() {
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000), 480.0);
        deixarPagavel(v);

        BigDecimal caixaAntes = financeiro.caixa().getSaldo();
        BigDecimal carteiraAntes = usuarios.findById(motorista.getId()).orElseThrow().getSaldoCarteira();

        Pagamento p = financeiro.pagar(motorista.getId(), List.of(v.getId()), "acerto", gestor);

        assertThat(financeiro.caixa().getSaldo())
                .describedAs("o mesmo valor que saiu do caixa")
                .isEqualByComparingTo(caixaAntes.subtract(p.getValor()));
        assertThat(usuarios.findById(motorista.getId()).orElseThrow().getSaldoCarteira())
                .describedAs("entra na carteira")
                .isEqualByComparingTo(carteiraAntes.add(p.getValor()));

        assertThat(movimentosCarteira.findAll())
                .describedAs("todo crédito deixa extrato")
                .anyMatch(m -> m.getTipo() == MovimentoCarteira.Tipo.ACERTO
                        && m.getValor().compareTo(p.getValor()) == 0);
    }

    @Test
    @DisplayName("a comissão é o km rodado vezes o valor por km")
    void comissaoEhKmVezesValorPorKm() {
        BigDecimal porKm = financeiro.caixa().getValorKmPadrao();

        Viagem v = rodarViagem(BigDecimal.valueOf(25_000), 512.4);
        deixarPagavel(v);

        assertThat(financeiro.kmDe(v)).isEqualByComparingTo(BigDecimal.valueOf(512.4));

        Pagamento p = financeiro.pagar(motorista.getId(), List.of(v.getId()), "acerto", gestor);

        assertThat(p.getValor())
                .describedAs("512,4 km a %s/km", porKm)
                .isEqualByComparingTo(BigDecimal.valueOf(512.4).multiply(porKm).setScale(2, RoundingMode.HALF_UP));
        assertThat(p.getBaseKm()).isEqualByComparingTo(BigDecimal.valueOf(512.4));
        assertThat(p.getValorKmAplicado()).isEqualByComparingTo(porKm);
    }

    @Test
    @DisplayName("carga cara não paga mais: o que conta é a distância, não o frete")
    void freteMaiorNaoAumentaAComissao() {
        // Mesma distância, fretes muito diferentes — a comissão tem que ser igual.
        Viagem barata = rodarViagem(BigDecimal.valueOf(10_000), 300.0);
        deixarPagavel(barata);
        BigDecimal comissaoBarata = financeiro
                .pagar(motorista.getId(), List.of(barata.getId()), "carga leve", gestor).getValor();

        Viagem cara = rodarViagem(BigDecimal.valueOf(30_000), 300.0);
        deixarPagavel(cara);
        Pagamento pagoCara = financeiro
                .pagar(motorista.getId(), List.of(cara.getId()), "carga pesada", gestor);

        assertThat(cara.getValorFrete())
                .describedAs("o frete realmente é maior")
                .isGreaterThan(barata.getValorFrete());
        assertThat(pagoCara.getValor())
                .describedAs("mas a comissão não muda: mesma distância, mesmo pagamento")
                .isEqualByComparingTo(comissaoBarata);
    }

    @Test
    @DisplayName("viagem sem telemetria não tem km confirmado, e sem km não há comissão")
    void semTelemetriaNaoPagaComissao() {
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000));   // sem telemetria
        viagens.liberar(v.getId(), gestor, "liberada na mão");

        assertThat(financeiro.kmDe(v)).isEqualByComparingTo(BigDecimal.ZERO);

        Pagamento p = financeiro.pagar(motorista.getId(), List.of(v.getId()), "sem km", gestor);
        assertThat(p.getValor())
                .describedAs("pagar km que ninguém mediu abriria pela porta dos fundos "
                        + "a fraude que a conferência fecha na frente")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("o valor por km próprio do motorista sobrepõe o padrão da empresa")
    void valorPorKmDoMotoristaSobrepoe() {
        BigDecimal padrao = financeiro.caixa().getValorKmPadrao();
        try {
            Viagem v1 = rodarViagem(BigDecimal.valueOf(25_000), 400.0);
            deixarPagavel(v1);
            BigDecimal noPadrao = financeiro
                    .pagar(motorista.getId(), List.of(v1.getId()), "no padrão", gestor).getValor();

            // Mesma distância — muda só o valor por km do motorista.
            motorista.setValorKmComissao(padrao.multiply(BigDecimal.valueOf(2)));
            usuarios.save(motorista);

            Viagem v2 = rodarViagem(BigDecimal.valueOf(25_000), 400.0);
            deixarPagavel(v2);
            Pagamento dobrado = financeiro
                    .pagar(motorista.getId(), List.of(v2.getId()), "no dobro", gestor);

            assertThat(dobrado.getValorKmAplicado())
                    .isEqualByComparingTo(padrao.multiply(BigDecimal.valueOf(2)));
            assertThat(dobrado.getValor())
                    .describedAs("valor por km dobrado, comissão dobrada")
                    .isEqualByComparingTo(noPadrao.multiply(BigDecimal.valueOf(2)));
        } finally {
            // Não deixa o valor próprio vazar para os outros testes desta classe.
            motorista.setValorKmComissao(null);
            usuarios.save(motorista);
        }
    }

    @Test
    @DisplayName("o acerto guarda a conta: quantas viagens, que percentual, que bases")
    void acertoGuardaAConta() {
        Viagem a = rodarViagem(BigDecimal.valueOf(20_000), 250.0);
        deixarPagavel(a);
        Viagem b = rodarViagem(BigDecimal.valueOf(30_000), 350.0);
        deixarPagavel(b);

        Pagamento p = financeiro.pagar(motorista.getId(), List.of(a.getId(), b.getId()),
                "duas de uma vez", gestor);

        BigDecimal freteEsperado = a.getValorFrete().add(b.getValorFrete());
        assertThat(p.getBaseFrete())
                .describedAs("a base registrada tem que bater com as viagens pagas")
                .isEqualByComparingTo(freteEsperado);
        assertThat(p.getCriadoPor().getId()).isEqualTo(gestor.getId());
        assertThat(pagamentos.findById(p.getId())).isPresent();

        assertThat(financeiro.pagaveis(motorista.getId()))
                .describedAs("as duas saíram da fila")
                .noneMatch(x -> x.getId().equals(a.getId()) || x.getId().equals(b.getId()));
    }

    // ----- apoio -----

    /**
     * Deixa a viagem pronta para acerto.
     *
     * Viagem com telemetria passa direto na conferência; só a que ficou retida
     * precisa da liberação do gestor — e `liberar()` recusa quem não está
     * retida, de propósito.
     */
    private void deixarPagavel(Viagem v) {
        if (viagemRepo.findById(v.getId()).orElseThrow()
                .getConferencia() == Viagem.Conferencia.RETIDA) {
            viagens.liberar(v.getId(), gestor, "conferido");
        }
    }

    /** Sem telemetria: sai retida e sem km confirmado. */
    private Viagem rodarViagem(BigDecimal pesoKg) {
        return rodarViagem(pesoKg, null);
    }

    /**
     * Roda a viagem e, se `kmRodados` vier preenchido, grava a distância que o
     * jogo teria confirmado — é dela que a comissão sai.
     */
    private Viagem rodarViagem(BigDecimal pesoKg, Double kmRodados) {
        NovaDemandaRequest req = new NovaDemandaRequest();
        req.origem = "Sinop";
        req.destino = "Cuiabá";
        req.empresaRemetente = "Agro Sinop Cereais";
        req.empresaDestinataria = "Frigorífico Vale Verde";
        req.carga = "Soja";
        req.quantidadeTotalKg = pesoKg;
        req.fretePorTonelada = BigDecimal.valueOf(180);
        UUID demandaId = demandas.criar(req).id();

        AceitarDemandaRequest aceite = new AceitarDemandaRequest();
        aceite.pesoKg = pesoKg;
        aceite.caminhaoId = caminhao.getId();

        UUID id = demandas.aceitar(demandaId, motorista.getId(), aceite).id();
        viagens.iniciar(id);

        if (kmRodados != null) {
            TelemetriaViagem t = new TelemetriaViagem();
            t.setViagem(viagemRepo.findById(id).orElseThrow());
            t.setDistanciaConfirmadaKm(kmRodados);
            telemetrias.save(t);
        }

        viagens.finalizar(id, "entregue", false);
        return viagemRepo.findById(id).orElseThrow();
    }
}
