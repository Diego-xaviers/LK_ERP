package com.lktransportes.dto;

import com.lktransportes.model.Viagem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Viagem como ela aparece no mural da empresa — todo mundo vê.
 *
 * De propósito NÃO traz eventos, documentos nem observação final: isso é o
 * detalhe operacional do motorista e só ele (ou um gestor) enxerga. Aqui fica
 * o que faz sentido ser público internamente: quem levou o quê, para onde e
 * quanto rendeu.
 */
public record ViagemResumoResponse(
        UUID id,
        Integer numero,
        String motorista,
        String caminhao,
        String placaCaminhao,
        String origem,
        String destino,
        String carga,
        BigDecimal pesoKg,
        BigDecimal valorFrete,
        BigDecimal totalDespesas,
        String status,
        /** Rótulo da conferência; os motivos ficam fora do mural. */
        String conferencia,
        LocalDateTime criadaEm,
        LocalDateTime finalizadaEm
) {
    public static ViagemResumoResponse de(Viagem v) {
        return new ViagemResumoResponse(
                v.getId(), v.getNumero(),
                v.getMotorista().getNome(),
                v.getCaminhao().getMarca() + " " + v.getCaminhao().getModelo(),
                v.getCaminhao().getPlaca(),
                v.getOrigem(), v.getDestino(), v.getCarga(), v.getPesoKg(), v.getValorFrete(),
                v.totalDespesas(),
                v.getStatus().name(),
                v.getConferencia() == null ? null : v.getConferencia().name(),
                v.getCriadaEm(), v.getFinalizadaEm()
        );
    }
}
