package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Extrato do caixa. Cada linha explica uma mudança no saldo. */
@Entity
@Table(name = "movimentos_caixa")
public class MovimentoCaixa {

    @Id @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    /** Sempre positivo: o sinal está no tipo. */
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 300)
    private String descricao;

    @Column(name = "saldo_depois", nullable = false, precision = 16, scale = 2)
    private BigDecimal saldoDepois;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @ManyToOne @JoinColumn(name = "viagem_id")
    private Viagem viagem;

    @ManyToOne @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @ManyToOne @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    public enum Tipo {
        /** Frete de uma viagem aprovada entrando no caixa. */
        FRETE,
        /** Comissão paga a um motorista. */
        COMISSAO,
        /** Aporte ou retirada lançada à mão pelo gestor. */
        AJUSTE
    }

    public boolean entrada() { return tipo == Tipo.FRETE || (tipo == Tipo.AJUSTE && positivo); }

    /** Só faz sentido em AJUSTE: diz se foi aporte ou retirada. */
    @Column(nullable = false)
    private boolean positivo = true;

    public UUID getId() { return id; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo t) { this.tipo = t; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal v) { this.valor = v; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String v) { this.descricao = v; }
    public BigDecimal getSaldoDepois() { return saldoDepois; }
    public void setSaldoDepois(BigDecimal v) { this.saldoDepois = v; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public Viagem getViagem() { return viagem; }
    public void setViagem(Viagem v) { this.viagem = v; }
    public Pagamento getPagamento() { return pagamento; }
    public void setPagamento(Pagamento p) { this.pagamento = p; }
    public Usuario getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Usuario u) { this.registradoPor = u; }
    public boolean isPositivo() { return positivo; }
    public void setPositivo(boolean v) { this.positivo = v; }
}
