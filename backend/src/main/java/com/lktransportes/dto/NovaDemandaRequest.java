package com.lktransportes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class NovaDemandaRequest {
    @NotBlank public String origem;
    @NotBlank public String destino;
    @NotBlank public String empresaRemetente;
    @NotBlank public String empresaDestinataria;
    @NotBlank public String carga;

    @NotNull @DecimalMin(value = "1", message = "A quantidade precisa ser maior que zero")
    public BigDecimal quantidadeTotalKg;

    @NotNull @DecimalMin(value = "0.01", message = "Informe o frete por tonelada")
    public BigDecimal fretePorTonelada;

    public BigDecimal valorCargaPorTonelada;
    public String observacoes;

    /** Data limite da demanda. Opcional. */
    public java.time.LocalDate prazoEntrega;

    /** Vazio = qualquer caminhão da frota. */
    public java.util.Set<java.util.UUID> caminhoesPermitidos;

    /** Vazio = qualquer reboque. */
    public java.util.Set<String> tiposReboquePermitidos;
}
