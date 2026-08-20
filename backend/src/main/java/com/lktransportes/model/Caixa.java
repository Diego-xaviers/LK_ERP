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

    /**
     * Quanto o motorista ganha por quilômetro rodado, quando não tem valor
     * próprio. É assim que muita transportadora paga motorista de verdade —
     * e, diferente do percentual sobre o frete, não transforma uma carga cara
     * em fortuna por uma viagem só.
     */
    @Column(name = "valor_km_padrao", nullable = false, precision = 8, scale = 3)
    private BigDecimal valorKmPadrao = new BigDecimal("0.350");

    public UUID getId() { return id; }
    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal v) { this.saldo = v; }
    public BigDecimal getValorKmPadrao() { return valorKmPadrao; }
    public void setValorKmPadrao(BigDecimal v) { this.valorKmPadrao = v; }

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
