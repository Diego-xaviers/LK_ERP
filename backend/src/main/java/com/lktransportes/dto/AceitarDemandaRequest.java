package com.lktransportes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O motorista escolhe só quanto vai levar e com qual equipamento.
 * Rota, carga e valores vêm da demanda — de propósito não estão aqui.
 */
public class AceitarDemandaRequest {
    @NotNull @DecimalMin(value = "1", message = "Informe quanto vai levar")
    public BigDecimal pesoKg;

    @NotNull public UUID caminhaoId;
    public UUID carretaId;
}
