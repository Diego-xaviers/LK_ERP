package com.lktransportes.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class AbastecimentoRequest {
    public UUID postoId;
    public BigDecimal litros;
    public BigDecimal valorLitro;
    /** PNG em base64 vindo do canvas de assinatura. */
    public String assinaturaBase64;
    public LocalDateTime ocorridoEm;
    public String observacao;
}
