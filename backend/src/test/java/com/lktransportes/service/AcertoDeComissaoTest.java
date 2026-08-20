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
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000));
        viagens.liberar(v.getId(), gestor, "conferido");

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
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000));
        viagens.liberar(v.getId(), gestor, "conferido");

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
        Viagem v = rodarViagem(BigDecimal.valueOf(25_000));
        viagens.liberar(v.getId(), gestor, "conferido");

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
    @DisplayName("o percentual próprio do motorista sobrepõe o padrão da empresa")
    void percentualDoMotoristaSobrepoe() {
        BigDecimal padrao = financeiro.caixa().getPercentualComissaoPadrao();
        try {
            Viagem v1 = rodarViagem(BigDecimal.valueOf(25_000));
            viagens.liberar(v1.getId(), gestor, "conferido");
            BigDecimal noPadrao = financeiro
                    .pagar(motorista.getId(), List.of(v1.getId()), "no padrão", gestor).getValor();

            // Mesma viagem, mesmo frete — muda só o percentual do motorista.
            motorista.setPercentualComissao(padrao.multiply(BigDecimal.valueOf(2)));
            usuarios.save(motorista);

            Viagem v2 = rodarViagem(BigDecimal.valueOf(25_000));
            viagens.liberar(v2.getId(), gestor, "conferido");
            Pagamento dobrado = financeiro
                    .pagar(motorista.getId(), List.of(v2.getId()), "no dobro", gestor);

            assertThat(dobrado.getPercentualAplicado())
                    .isEqualByComparingTo(padrao.multiply(BigDecimal.valueOf(2)));
            assertThat(dobrado.getValor())
                    .describedAs("percentual dobrado, comissão dobrada")
                    .isEqualByComparingTo(noPadrao.multiply(BigDecimal.valueOf(2)));
        } finally {
            // Não deixa o percentual vazar para os outros testes desta classe.
            motorista.setPercentualComissao(null);
            usuarios.save(motorista);
        }
    }

    @Test
    @DisplayName("o acerto guarda a conta: quantas viagens, que percentual, que bases")
    void acertoGuardaAConta() {
        Viagem a = rodarViagem(BigDecimal.valueOf(20_000));
        viagens.liberar(a.getId(), gestor, "conferido");
        Viagem b = rodarViagem(BigDecimal.valueOf(30_000));
        viagens.liberar(b.getId(), gestor, "conferido");

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

    /** Pega carga, sai e entrega. Sem telemetria, então sai retida. */
    private Viagem rodarViagem(BigDecimal pesoKg) {
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
        viagens.finalizar(id, "entregue", false);
        return viagemRepo.findById(id).orElseThrow();
    }
}
