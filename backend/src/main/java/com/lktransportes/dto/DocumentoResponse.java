package com.lktransportes.dto;

import com.lktransportes.model.DocumentoViagem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentoResponse(
        UUID id,
        String tipo,
        Integer numero,
        String serie,
        String chaveInterna,
        LocalDateTime geradoEm,
        String motorista,
        String caminhao,
        String placaCaminhao,
        String carreta,
        String placaCarreta,
        String remetente,
        String destinatario,
        String origem,
        String destino,
        String carga,
        BigDecimal pesoKg,
        BigDecimal valorCarga,
        BigDecimal valorFrete,
        Integer numeroViagem
) {
    public static DocumentoResponse de(DocumentoViagem d) {
        return new DocumentoResponse(
                d.getId(), d.getTipo().name(), d.getNumero(), d.getSerie(),
                d.getChaveInterna(), d.getGeradoEm(),
                d.getSnapMotorista(), d.getSnapCaminhao(), d.getSnapPlacaCaminhao(),
                d.getSnapCarreta(), d.getSnapPlacaCarreta(),
                d.getSnapRemetente(), d.getSnapDestinatario(),
                d.getSnapOrigem(), d.getSnapDestino(),
                d.getSnapCarga(), d.getSnapPesoKg(), d.getSnapValorCarga(), d.getSnapValorFrete(),
                d.getViagem().getNumero()
        );
    }
}
