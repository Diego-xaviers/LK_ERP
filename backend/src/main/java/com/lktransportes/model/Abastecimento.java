package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Abastecimento em posto cadastrado, com assinatura do motorista. */
@Entity
@DiscriminatorValue("ABASTECIMENTO")
public class Abastecimento extends EventoViagem {

    /** Anulável no schema por causa do SINGLE_TABLE — ver nota em Manutencao. */
    @ManyToOne
    @JoinColumn(name = "posto_id")
    private Posto posto;

    @Column(precision = 10, scale = 3)
    private BigDecimal litros;

    @Column(name = "valor_litro", precision = 10, scale = 3)
    private BigDecimal valorLitro;

    /** Assinatura desenhada no canvas, guardada como PNG em base64. */
    @Lob
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONGVARCHAR)
    @Column(name = "assinatura_base64", columnDefinition = "text")
    private String assinaturaBase64;

    /** total = litros x valor por litro — calculado, nunca digitado. */
    public void calcularTotal() {
        if (litros != null && valorLitro != null) {
            setValor(litros.multiply(valorLitro).setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Override
    public String descricaoCurta() {
        return "Abastecimento — " + (posto != null ? posto.getNome() : "");
    }

    public Posto getPosto() { return posto; }
    public void setPosto(Posto posto) { this.posto = posto; }
    public BigDecimal getLitros() { return litros; }
    public void setLitros(BigDecimal litros) { this.litros = litros; }
    public BigDecimal getValorLitro() { return valorLitro; }
    public void setValorLitro(BigDecimal v) { this.valorLitro = v; }
    public String getAssinaturaBase64() { return assinaturaBase64; }
    public void setAssinaturaBase64(String v) { this.assinaturaBase64 = v; }
}
