package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Um acerto com o motorista: fecha um conjunto de viagens e paga a comissão
 * delas de uma vez. As viagens ficam amarradas ao pagamento, então não há como
 * pagar a mesma viagem duas vezes.
 */
@Entity
@Table(name = "pagamentos")
public class Pagamento {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private Integer numero;

    @ManyToOne(optional = false) @JoinColumn(name = "motorista_id")
    private Usuario motorista;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    /** Valor por km aplicado neste acerto — guardado para o recibo não mudar depois. */
    @Column(name = "valor_km_aplicado", nullable = false, precision = 8, scale = 3)
    private BigDecimal valorKmAplicado;

    /** Quilometragem paga, somando as viagens do acerto. */
    @Column(name = "base_km", nullable = false, precision = 12, scale = 1)
    private BigDecimal baseKm = BigDecimal.ZERO;

    /** Soma dos fretes das viagens pagas, antes de descontar despesas. */
    @Column(name = "base_frete", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseFrete = BigDecimal.ZERO;

    @Column(name = "base_despesas", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseDespesas = BigDecimal.ZERO;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @ManyToOne @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(length = 400)
    private String observacao;

    @OneToMany(mappedBy = "pagamento")
    private List<Viagem> viagens = new ArrayList<>();

    public UUID getId() { return id; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer v) { this.numero = v; }
    public Usuario getMotorista() { return motorista; }
    public void setMotorista(Usuario u) { this.motorista = u; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal v) { this.valor = v; }
    public BigDecimal getValorKmAplicado() { return valorKmAplicado; }
    public void setValorKmAplicado(BigDecimal v) { this.valorKmAplicado = v; }
    public BigDecimal getBaseKm() { return baseKm; }
    public void setBaseKm(BigDecimal v) { this.baseKm = v; }
    public BigDecimal getBaseFrete() { return baseFrete; }
    public void setBaseFrete(BigDecimal v) { this.baseFrete = v; }
    public BigDecimal getBaseDespesas() { return baseDespesas; }
    public void setBaseDespesas(BigDecimal v) { this.baseDespesas = v; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario u) { this.criadoPor = u; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String v) { this.observacao = v; }
    public List<Viagem> getViagens() { return viagens; }
}
