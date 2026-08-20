package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Extrato da carteira do motorista. O saldo dele fica em Usuario, e cada
 * mudança tem uma linha aqui explicando o porquê — mesma ideia do caixa da
 * empresa: saldo sem extrato é número que ninguém consegue defender.
 */
@Entity
@Table(name = "movimentos_carteira")
public class MovimentoCarteira {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false) @JoinColumn(name = "motorista_id")
    private Usuario motorista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    /** Sempre positivo: o sentido está em `positivo`. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private boolean positivo;

    @Column(nullable = false, length = 300)
    private String descricao;

    @Column(name = "saldo_depois", nullable = false, precision = 14, scale = 2)
    private BigDecimal saldoDepois;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public enum Tipo {
        /** Comissão recebida num acerto. */
        ACERTO,
        /** Gasto na loja. */
        COMPRA,
        /** Crédito ou débito lançado pelo gestor. */
        AJUSTE
    }

    public UUID getId() { return id; }
    public Usuario getMotorista() { return motorista; }
    public void setMotorista(Usuario u) { this.motorista = u; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo t) { this.tipo = t; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal v) { this.valor = v; }
    public boolean isPositivo() { return positivo; }
    public void setPositivo(boolean v) { this.positivo = v; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String v) { this.descricao = v; }
    public BigDecimal getSaldoDepois() { return saldoDepois; }
    public void setSaldoDepois(BigDecimal v) { this.saldoDepois = v; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
