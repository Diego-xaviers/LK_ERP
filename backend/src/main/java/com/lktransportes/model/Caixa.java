package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * O caixa da transportadora. Linha única: o saldo é um número só, e todo
 * movimento que o altera fica registrado em MovimentoCaixa — é isso que permite
 * explicar de onde veio e para onde foi cada centavo.
 */
@Entity
@Table(name = "caixa")
public class Caixa {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    /** Usado quando o motorista não tem percentual próprio. */
    @Column(name = "percentual_comissao_padrao", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualComissaoPadrao = new BigDecimal("12.00");

    public UUID getId() { return id; }
    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal v) { this.saldo = v; }
    public BigDecimal getPercentualComissaoPadrao() { return percentualComissaoPadrao; }
    public void setPercentualComissaoPadrao(BigDecimal v) { this.percentualComissaoPadrao = v; }

    public void creditar(BigDecimal valor) { this.saldo = this.saldo.add(valor); }

    /** Debita sem deixar o caixa negativo — pagar mais do que se tem é erro, não saldo negativo. */
    public void debitar(BigDecimal valor) {
        if (saldo.compareTo(valor) < 0) {
            throw new IllegalStateException(
                    "Saldo insuficiente no caixa: tem R$ %s e a saída é de R$ %s."
                            .formatted(saldo.toPlainString(), valor.toPlainString()));
        }
        this.saldo = this.saldo.subtract(valor);
    }
}
