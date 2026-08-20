package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compra feita por um motorista na loja.
 *
 * Guarda o nome e o preço no momento da compra, não só a referência ao item:
 * mudar o preço na loja depois não pode reescrever o que já foi comprado.
 */
@Entity
@Table(name = "compras")
public class Compra {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false) @JoinColumn(name = "motorista_id")
    private Usuario motorista;

    @ManyToOne @JoinColumn(name = "item_id")
    private ItemLoja item;

    @Column(name = "nome_item", nullable = false, length = 120)
    private String nomeItem;

    @Column(nullable = false)
    private Integer quantidade = 1;

    @Column(name = "valor_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public UUID getId() { return id; }
    public Usuario getMotorista() { return motorista; }
    public void setMotorista(Usuario u) { this.motorista = u; }
    public ItemLoja getItem() { return item; }
    public void setItem(ItemLoja i) { this.item = i; }
    public String getNomeItem() { return nomeItem; }
    public void setNomeItem(String v) { this.nomeItem = v; }
    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer v) { this.quantidade = v; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public void setValorUnitario(BigDecimal v) { this.valorUnitario = v; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal v) { this.valorTotal = v; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
