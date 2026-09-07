package com.lktransportes.service;

import com.lktransportes.model.*;
import com.lktransportes.repository.EventoViagemRepository;
import com.lktransportes.repository.ViagemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Registra os custos definitivos do job sem duplicar o que já veio do agente. */
@Service
public class DespesasVtlogService {
    private static final BigDecimal LIMITE = new BigDecimal("999999999999.99");
    private final EventoViagemRepository eventos;
    private final ViagemRepository viagens;

    public DespesasVtlogService(EventoViagemRepository eventos, ViagemRepository viagens) {
        this.eventos = eventos;
        this.viagens = viagens;
    }

    @Transactional
    public void registrar(Viagem viagem, VtlogService.EntregaVtlog entrega) {
        registrarCombustivel(viagem, entrega);
        registrarManutencao(viagem, entrega);
        registrarPedagios(viagem, entrega);
    }

    private void registrarCombustivel(Viagem v, VtlogService.EntregaVtlog e) {
        if (e.totalCombustivel() == null) return;
        BigDecimal total = dinheiro(e.totalCombustivel(), "combustível");
        String chave = "vtlog:job:" + e.jobId() + ":combustivel";
        EventoViagem existente = eventos.findByChaveExterna(chave).orElse(null);
        if (existente != null) {
            exigirMesmaViagem(v, existente);
            atualizarValor(v, existente, total, "combustível");
            return;
        }
        if (total.signum() == 0) return;
        Abastecimento a = new Abastecimento();
        a.setViagem(v); a.setChaveExterna(chave); a.setOrigem(EventoViagem.Origem.TELEMETRIA);
        a.setLitros(decimal(e.litrosCombustivel(), 3));
        a.setValorLitro(e.precoCombustivel() == null ? null : e.precoCombustivel().setScale(3, RoundingMode.HALF_UP));
        a.setValor(total); a.setOcorridoEm(fim(e));
        a.setObservacao("Custo total de combustível confirmado pelo VTLog para esta viagem.");
        salvar(v, a);
    }

    private void registrarManutencao(Viagem v, VtlogService.EntregaVtlog e) {
        if (e.totalManutencao() == null) return;
        BigDecimal total = dinheiro(e.totalManutencao(), "manutenção");
        String chave = "vtlog:job:" + e.jobId() + ":manutencao";
        EventoViagem existente = eventos.findByChaveExterna(chave).orElse(null);
        if (existente != null) {
            exigirMesmaViagem(v, existente);
            atualizarValor(v, existente, total, "manutenção");
            return;
        }
        if (total.signum() == 0) return;
        Manutencao m = new Manutencao();
        m.setViagem(v); m.setCaminhao(v.getCaminhao()); m.setChaveExterna(chave);
        m.setOrigem(EventoViagem.Origem.TELEMETRIA); m.setValor(total); m.setOcorridoEm(fim(e));
        m.setServico("Desgaste e reparos calculados pelo VTLog");
        m.setObservacao(e.detalheManutencao() == null || e.detalheManutencao().isBlank()
                ? "Custos de oficina confirmados no fechamento da entrega."
                : e.detalheManutencao());
        salvar(v, m);
    }

    private void registrarPedagios(Viagem v, VtlogService.EntregaVtlog e) {
        if (e.pedagios() == null) return;
        if (e.pedagios().size() > 500) throw new IllegalArgumentException("Quantidade de pedágios inválida.");
        for (VtlogService.EventoVtlog item : e.pedagios()) {
            if (item == null || item.id() == null || !item.id().matches("[0-9]{1,30}"))
                throw new IllegalArgumentException("Evento de pedágio VTLog inválido.");
            BigDecimal valor = dinheiro(item.valor(), "pedágio");
            if (valor.signum() == 0) continue;
            String chave = "vtlog:evento:" + item.id();
            EventoViagem existente = eventos.findByChaveExterna(chave).orElse(null);
            if (existente != null) {
                if (!existente.getViagem().getId().equals(v.getId()))
                    throw new IllegalStateException("Evento VTLog já vinculado a outra viagem.");
                atualizarValor(v, existente, valor, "pedágio");
                continue;
            }

            LocalDateTime ocorrido = data(item.ocorridoEpochMs(), fim(e));
            Pedagio legado = pedagioLegado(v, valor, ocorrido);
            if (legado != null) {
                legado.setChaveExterna(chave);
                eventos.save(legado);
                continue;
            }
            Pedagio p = new Pedagio();
            p.setViagem(v); p.setChaveExterna(chave); p.setValor(valor); p.setOcorridoEm(ocorrido);
            p.setOrigem(EventoViagem.Origem.TELEMETRIA);
            p.setLocal("Pedágio confirmado pelo VTLog");
            p.setObservacao(item.detalhe());
            salvar(v, p);
        }
    }

    /** Adota lançamentos das versões anteriores para não cobrar o mesmo pedágio duas vezes. */
    private Pedagio pedagioLegado(Viagem v, BigDecimal valor, LocalDateTime ocorrido) {
        return eventos.findByViagemId(v.getId()).stream()
                .filter(Pedagio.class::isInstance).map(Pedagio.class::cast)
                .filter(p -> p.getChaveExterna() == null && p.getOrigem() == EventoViagem.Origem.TELEMETRIA)
                .filter(p -> p.getValor() != null && p.getValor().compareTo(valor) == 0)
                .filter(p -> Math.abs(Duration.between(p.getOcorridoEm(), ocorrido).toMinutes()) <= 15)
                .min(Comparator.comparingLong(p -> Math.abs(Duration.between(p.getOcorridoEm(), ocorrido).toSeconds())))
                .orElse(null);
    }

    private void atualizarValor(Viagem v, EventoViagem evento, BigDecimal novo, String tipo) {
        if (evento.getValor() != null && evento.getValor().compareTo(novo) == 0) return;
        if (v.getPagamento() != null) {
            v.setPendenciaMultas("O VTLog corrigiu o custo de " + tipo + " após o acerto. Conferência financeira necessária.");
            viagens.save(v);
            return;
        }
        evento.setValor(novo);
        eventos.save(evento);
    }

    private void salvar(Viagem v, EventoViagem evento) {
        eventos.saveAndFlush(evento);
        if (!v.getEventos().contains(evento)) v.getEventos().add(evento);
        if (v.getPagamento() != null) {
            v.setPendenciaMultas("Despesa VTLog recebida após o acerto. Conferir a fatura; o pagamento anterior não foi alterado.");
            viagens.save(v);
        }
    }

    private void exigirMesmaViagem(Viagem v, EventoViagem evento) {
        if (!evento.getViagem().getId().equals(v.getId()))
            throw new IllegalStateException("Despesa VTLog já vinculada a outra viagem.");
    }

    private BigDecimal dinheiro(BigDecimal v, String nome) {
        if (v == null || v.signum() < 0 || v.compareTo(LIMITE) > 0)
            throw new IllegalArgumentException("Valor de " + nome + " inválido.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Double v, int escala) {
        return v == null || !Double.isFinite(v) || v < 0 ? null : BigDecimal.valueOf(v).setScale(escala, RoundingMode.HALF_UP);
    }

    private LocalDateTime fim(VtlogService.EntregaVtlog e) {
        return data(e.fimEpochMs(), LocalDateTime.now());
    }

    private LocalDateTime data(Long epoch, LocalDateTime padrao) {
        if (epoch == null || epoch <= 0) return padrao;
        try { return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDateTime(); }
        catch (DateTimeException ex) { return padrao; }
    }
}
