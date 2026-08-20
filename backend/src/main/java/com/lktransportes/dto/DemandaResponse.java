package com.lktransportes.dto;

import com.lktransportes.model.Caminhao;
import com.lktransportes.model.Demanda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record DemandaResponse(
        UUID id,
        Integer numero,
        String origem,
        String destino,
        String empresaRemetente,
        String empresaDestinataria,
        String carga,
        BigDecimal quantidadeTotalKg,
        BigDecimal quantidadeEntregueKg,
        /** O que falta entregar, sem descontar viagens em curso. */
        BigDecimal saldoKg,
        /** O que dá para pegar agora: o saldo menos o que já está reservado. */
        BigDecimal saldoDisponivelKg,
        BigDecimal reservadoKg,
        /** Quanto da demanda já foi cumprido, de 0 a 100. */
        BigDecimal percentualConcluido,
        BigDecimal fretePorTonelada,
        BigDecimal valorCargaPorTonelada,
        LocalDate prazoEntrega,
        boolean atrasada,
        List<EquipamentoPermitido> caminhoesPermitidos,
        Set<String> tiposReboquePermitidos,
        String status,
        boolean aceitaNovaViagem,
        LocalDateTime criadaEm,
        LocalDateTime concluidaEm,
        String observacoes
) {
    /** Só o suficiente para a tela mostrar e filtrar o seletor de caminhão. */
    public record EquipamentoPermitido(UUID id, String descricao, String placa) {}

    public static DemandaResponse de(Demanda d, BigDecimal reservadoKg) {
        BigDecimal reservado = reservadoKg == null ? BigDecimal.ZERO : reservadoKg;
        BigDecimal disponivel = d.saldoKg().subtract(reservado);
        if (disponivel.signum() < 0) disponivel = BigDecimal.ZERO;

        BigDecimal pct = BigDecimal.ZERO;
        if (d.getQuantidadeTotalKg().signum() > 0) {
            pct = d.getQuantidadeEntregueKg()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(d.getQuantidadeTotalKg(), 1, RoundingMode.HALF_UP);
        }

        List<EquipamentoPermitido> caminhoes = d.getCaminhoesPermitidos().stream()
                .sorted(java.util.Comparator.comparing(Caminhao::getPlaca))
                .map(c -> new EquipamentoPermitido(c.getId(),
                        c.getMarca() + " " + c.getModelo(), c.getPlaca()))
                .toList();

        return new DemandaResponse(
                d.getId(), d.getNumero(), d.getOrigem(), d.getDestino(),
                d.getEmpresaRemetente(), d.getEmpresaDestinataria(), d.getCarga(),
                d.getQuantidadeTotalKg(), d.getQuantidadeEntregueKg(), d.saldoKg(),
                disponivel, reservado, pct,
                d.getFretePorTonelada(), d.getValorCargaPorTonelada(),
                d.getPrazoEntrega(), d.estaAtrasada(),
                caminhoes, Set.copyOf(d.getTiposReboquePermitidos()),
                d.getStatus().name(),
                // Só oferece a carga se sobrar algo de verdade para pegar.
                d.aceitaNovaViagem() && disponivel.signum() > 0,
                d.getCriadaEm(), d.getConcluidaEm(), d.getObservacoes()
        );
    }
}
