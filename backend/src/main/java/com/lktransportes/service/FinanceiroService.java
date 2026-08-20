package com.lktransportes.service;

import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caixa da empresa e acerto com os motoristas.
 *
 * Regra de comissão combinada: percentual sobre o frete MENOS as despesas da
 * viagem (abastecimento, pedágio, multa, manutenção). Quem roda mal ganha
 * menos, porque o custo sai da mesma base.
 */
@Service
public class FinanceiroService {

    private final CaixaRepository caixas;
    private final MovimentoCaixaRepository movimentos;
    private final PagamentoRepository pagamentos;
    private final ViagemRepository viagens;
    private final UsuarioRepository usuarios;
    private final CarteiraService carteira;

    public FinanceiroService(CaixaRepository caixas, MovimentoCaixaRepository movimentos,
                             PagamentoRepository pagamentos, ViagemRepository viagens,
                             UsuarioRepository usuarios, CarteiraService carteira) {
        this.caixas = caixas;
        this.movimentos = movimentos;
        this.pagamentos = pagamentos;
        this.viagens = viagens;
        this.usuarios = usuarios;
        this.carteira = carteira;
    }

    /** Linha única, criada na primeira vez que alguém olha o financeiro. */
    @Transactional
    public Caixa caixa() {
        return caixas.findAll().stream().findFirst().orElseGet(() -> caixas.save(new Caixa()));
    }

    // ------------------------------------------------------------------
    // Comissão
    // ------------------------------------------------------------------

    public BigDecimal percentualDe(Usuario motorista) {
        return motorista.getPercentualComissao() != null
                ? motorista.getPercentualComissao()
                : caixa().getPercentualComissaoPadrao();
    }

    /** Base do cálculo: frete menos despesas, nunca negativa. */
    public BigDecimal baseDe(Viagem v) {
        BigDecimal frete = v.getValorFrete() == null ? BigDecimal.ZERO : v.getValorFrete();
        BigDecimal base = frete.subtract(v.totalDespesas());
        return base.signum() < 0 ? BigDecimal.ZERO : base;
    }

    public BigDecimal comissaoDe(Viagem v, BigDecimal percentual) {
        return baseDe(v).multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Entrada de frete
    // ------------------------------------------------------------------

    /**
     * O frete entra no caixa quando a viagem passa a valer — na aprovação, ou
     * na liberação do gestor se ela tinha sido retida.
     */
    @Transactional
    public void creditarFrete(Viagem v) {
        if (!v.liberadaParaPagamento()) return;
        BigDecimal frete = v.getValorFrete();
        if (frete == null || frete.signum() <= 0) return;

        Caixa c = caixa();
        c.creditar(frete);
        caixas.save(c);

        MovimentoCaixa m = new MovimentoCaixa();
        m.setTipo(MovimentoCaixa.Tipo.FRETE);
        m.setValor(frete);
        m.setDescricao("Frete da viagem #" + v.getNumero() + " — " + v.getOrigem() + " a " + v.getDestino());
        m.setSaldoDepois(c.getSaldo());
        m.setViagem(v);
        movimentos.save(m);
    }

    // ------------------------------------------------------------------
    // Acerto com o motorista
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Viagem> pagaveis(UUID motoristaId) {
        return viagens.pagaveis(motoristaId);
    }

    @Transactional
    public Pagamento pagar(UUID motoristaId, List<UUID> viagemIds, String observacao, Usuario gestor) {
        Usuario motorista = usuarios.findById(motoristaId).orElseThrow();
        List<Viagem> selecionadas = new ArrayList<>();

        for (Viagem v : viagens.pagaveis(motoristaId)) {
            if (viagemIds == null || viagemIds.isEmpty() || viagemIds.contains(v.getId())) {
                selecionadas.add(v);
            }
        }
        if (selecionadas.isEmpty()) {
            throw new IllegalStateException("Não há viagem liberada para acertar com este motorista.");
        }

        BigDecimal percentual = percentualDe(motorista);
        BigDecimal totalFrete = BigDecimal.ZERO;
        BigDecimal totalDespesas = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (Viagem v : selecionadas) {
            totalFrete = totalFrete.add(v.getValorFrete() == null ? BigDecimal.ZERO : v.getValorFrete());
            totalDespesas = totalDespesas.add(v.totalDespesas());
            total = total.add(comissaoDe(v, percentual));
        }

        Caixa c = caixa();
        c.debitar(total);          // recusa se não houver saldo
        caixas.save(c);

        Pagamento p = new Pagamento();
        p.setNumero(pagamentos.ultimoNumero() + 1);
        p.setMotorista(motorista);
        p.setValor(total);
        p.setPercentualAplicado(percentual);
        p.setBaseFrete(totalFrete);
        p.setBaseDespesas(totalDespesas);
        p.setObservacao(observacao);
        p.setCriadoPor(gestor);
        pagamentos.save(p);

        // Amarrar as viagens ao acerto é o que impede pagar a mesma duas vezes.
        for (Viagem v : selecionadas) {
            v.setPagamento(p);
            viagens.save(v);
        }

        MovimentoCaixa m = new MovimentoCaixa();
        m.setTipo(MovimentoCaixa.Tipo.COMISSAO);
        m.setValor(total);
        m.setDescricao("Acerto #%d com %s — %d viagem(ns) a %s%%".formatted(
                p.getNumero(), motorista.getNome(), selecionadas.size(),
                percentual.stripTrailingZeros().toPlainString()));
        m.setSaldoDepois(c.getSaldo());
        m.setPagamento(p);
        m.setRegistradoPor(gestor);
        movimentos.save(m);

        // O acerto não é só um registro: vira crédito na carteira do motorista.
        carteira.creditar(motorista, total, com.lktransportes.model.MovimentoCarteira.Tipo.ACERTO,
                "Acerto #" + p.getNumero() + " — " + selecionadas.size() + " viagem(ns)");

        return p;
    }

    // ------------------------------------------------------------------
    // Ajuste manual e configuração
    // ------------------------------------------------------------------

    @Transactional
    public Caixa ajustar(BigDecimal valor, String descricao, Usuario gestor) {
        if (valor == null || valor.signum() == 0) {
            throw new IllegalArgumentException("Informe um valor diferente de zero.");
        }
        Caixa c = caixa();
        boolean aporte = valor.signum() > 0;
        BigDecimal absoluto = valor.abs();

        if (aporte) {
            c.creditar(absoluto);
        } else {
            c.debitar(absoluto);
        }
        caixas.save(c);

        MovimentoCaixa m = new MovimentoCaixa();
        m.setTipo(MovimentoCaixa.Tipo.AJUSTE);
        m.setValor(absoluto);
        m.setPositivo(aporte);
        m.setDescricao((descricao == null || descricao.isBlank())
                ? (aporte ? "Aporte no caixa" : "Retirada do caixa")
                : descricao);
        m.setSaldoDepois(c.getSaldo());
        m.setRegistradoPor(gestor);
        movimentos.save(m);
        return c;
    }

    @Transactional
    public Caixa definirPercentualPadrao(BigDecimal percentual) {
        Caixa c = caixa();
        c.setPercentualComissaoPadrao(percentual);
        return caixas.save(c);
    }

    @Transactional(readOnly = true)
    public List<MovimentoCaixa> extrato(int quantos) {
        return movimentos.findByOrderByCriadoEmDesc(Limit.of(quantos));
    }

    @Transactional(readOnly = true)
    public List<Pagamento> pagamentosDe(UUID motoristaId) {
        return motoristaId == null
                ? pagamentos.findAllByOrderByCriadoEmDesc()
                : pagamentos.findByMotoristaIdOrderByCriadoEmDesc(motoristaId);
    }
}
