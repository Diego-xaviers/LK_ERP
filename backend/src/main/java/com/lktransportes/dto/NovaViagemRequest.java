package com.lktransportes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class NovaViagemRequest {
    @NotBlank public String origem;
    @NotBlank public String destino;
    @NotBlank public String empresaRemetente;
    @NotBlank public String empresaDestinataria;
    @NotBlank public String carga;
    @NotNull  public BigDecimal pesoKg;
    public BigDecimal valorCarga;
    public BigDecimal valorFrete;
    @NotNull public UUID motoristaId;
    @NotNull public UUID caminhaoId;
    public UUID carretaId;
}
