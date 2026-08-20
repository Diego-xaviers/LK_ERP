package com.lktransportes.dto;

import com.lktransportes.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoResponse(
        UUID id,
        String tipo,
        String descricao,
        BigDecimal valor,
        LocalDateTime ocorridoEm,
        String observacao,
        // específicos de abastecimento — usados pelo comprovante
        String posto,
        BigDecimal litros,
        BigDecimal valorLitro,
        boolean temAssinatura
) {
    public static EventoResponse de(EventoViagem e) {
        String tipo = switch (e) {
            case Abastecimento ignored -> "ABASTECIMENTO";
            case Manutencao ignored -> "MANUTENCAO";
            case Pedagio ignored -> "PEDAGIO";
            case Multa ignored -> "MULTA";
            case Ocorrencia ignored -> "OCORRENCIA";
            default -> "EVENTO";
        };

        if (e instanceof Abastecimento a) {
            return new EventoResponse(a.getId(), tipo, a.descricaoCurta(), a.getValor(),
                    a.getOcorridoEm(), a.getObservacao(),
                    a.getPosto() != null ? a.getPosto().getNome() : null,
                    a.getLitros(), a.getValorLitro(),
                    a.getAssinaturaBase64() != null);
        }

        return new EventoResponse(e.getId(), tipo, e.descricaoCurta(), e.getValor(),
                e.getOcorridoEm(), e.getObservacao(), null, null, null, false);
    }
}
