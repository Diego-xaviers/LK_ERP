package com.lktransportes.dto;

import com.lktransportes.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resposta de viagem. Existe pra não serializar a entidade direto:
 * Viagem tem lista de eventos e cada evento aponta de volta pra viagem,
 * o que causaria recursão infinita no Jackson.
 */
public record ViagemResponse(
        UUID id,
        Integer numero,
        /** Nulo em viagem avulsa. Preenchido = o frete veio da tarifa, não digitado. */
        Integer demandaNumero,
        /** Id da demanda, para o painel oferecer a próxima viagem do mesmo contrato. */
        UUID demandaId,
        String origem,
        String destino,
        String empresaRemetente,
        String empresaDestinataria,
        String carga,
        BigDecimal pesoKg,
        BigDecimal valorCarga,
        BigDecimal valorFrete,
        String motorista,
        String caminhao,
        String placaCaminhao,
        String carreta,
        String placaCarreta,
        String status,
        LocalDateTime criadaEm,
        LocalDateTime iniciadaEm,
        LocalDateTime finalizadaEm,
        String observacaoFinal,
        Boolean houveAvaria,
        /** APROVADA, RETIDA ou LIBERADA. Nulo enquanto a viagem não terminou. */
        String conferencia,
        String motivosConferencia,
        boolean liberadaParaPagamento,
        String liberadaPor,
        java.time.LocalDateTime liberadaEm,
        String observacaoLiberacao,
        BigDecimal totalDespesas,
        List<EventoResponse> eventos,
        List<DocumentoResponse> documentos
) {
    public static ViagemResponse de(Viagem v, List<DocumentoViagem> documentos) {
        List<EventoResponse> eventos = v.getEventos().stream()
                .sorted(Comparator.comparing(EventoViagem::getOcorridoEm))
                .map(EventoResponse::de)
                .toList();

        return new ViagemResponse(
                v.getId(), v.getNumero(),
                v.getDemanda() != null ? v.getDemanda().getNumero() : null,
                v.getDemanda() != null ? v.getDemanda().getId() : null,
                v.getOrigem(), v.getDestino(),
                v.getEmpresaRemetente(), v.getEmpresaDestinataria(),
                v.getCarga(), v.getPesoKg(), v.getValorCarga(), v.getValorFrete(),
                v.getMotorista().getNome(),
                v.getCaminhao().getMarca() + " " + v.getCaminhao().getModelo(),
                v.getCaminhao().getPlaca(),
                v.getCarreta() != null ? v.getCarreta().getTipo() : null,
                v.getCarreta() != null ? v.getCarreta().getPlaca() : null,
                v.getStatus().name(),
                v.getCriadaEm(), v.getIniciadaEm(), v.getFinalizadaEm(),
                v.getObservacaoFinal(), v.getHouveAvaria(),
                v.getConferencia() == null ? null : v.getConferencia().name(),
                v.getMotivosConferencia(),
                v.liberadaParaPagamento(),
                v.getLiberadaPor() == null ? null : v.getLiberadaPor().getNome(),
                v.getLiberadaEm(), v.getObservacaoLiberacao(),
                v.totalDespesas(),
                eventos,
                documentos == null ? List.of() : documentos.stream().map(DocumentoResponse::de).toList()
        );
    }
}
