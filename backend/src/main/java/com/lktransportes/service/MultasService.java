package com.lktransportes.service;

import com.lktransportes.dto.TelemetriaPing.MultaAgente;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.*;

/** Escritas serializadas pelo motorista, com recibos persistentes por evento. */
@Service
public class MultasService {
    public static final String AGUARDANDO = "Aguardando o total definitivo de multas do VTLog.";
    public static final String SEM_TOTAL = "VTLog não informou o total de multas. Conferência manual necessária.";
    private final EventoViagemRepository eventos;
    private final ViagemRepository viagens;
    private final UsuarioRepository usuarios;
    private final CnhService cnhs;
    public MultasService(EventoViagemRepository eventos, ViagemRepository viagens,
                         UsuarioRepository usuarios, CnhService cnhs) {
        this.eventos = eventos; this.viagens = viagens; this.usuarios = usuarios; this.cnhs = cnhs;
    }

    public static BigDecimal dinheiro(BigDecimal valor) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new IllegalArgumentException("Valor de multa inválido.");
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public List<UUID> receber(UUID motorista, List<MultaAgente> lote) {
        if (lote == null || lote.isEmpty()) return List.of();
        if (lote.size() > 100) throw new IllegalArgumentException("Envie no máximo 100 multas por vez.");
        usuarios.bloquear(motorista).orElseThrow();
        List<UUID> confirmadas = new ArrayList<>();
        for (MultaAgente item : lote) {
            if (item == null || item.id() == null || item.agenteJobId() == null || item.ocorridoEm() == null)
                throw new IllegalArgumentException("Multa sem identificação da viagem ou horário.");
            BigDecimal valor = dinheiro(item.valor());
            if (valor.signum() == 0) throw new IllegalArgumentException("A multa deve ter valor positivo.");
            String chave = motorista + ":" + item.id();
            Optional<EventoViagem> existente = eventos.findByChaveExterna(chave);
            if (existente.isPresent()) {
                EventoViagem e = existente.get();
                if (!Objects.equals(e.getViagem().getAgenteJobId(), item.agenteJobId()) || e.getValor().compareTo(valor) != 0)
                    throw new IllegalArgumentException("Identificador de multa reutilizado com outros dados.");
                confirmadas.add(item.id());
                continue;
            }
            Optional<Viagem> destino = viagens.findByAgenteJobId(item.agenteJobId());
            if (destino.isEmpty()) continue; // fica na fila local até a viagem ser vinculada
            Viagem v = destino.get();
            if (!v.getMotorista().getId().equals(motorista))
                throw new IllegalArgumentException("Esta viagem pertence a outro motorista.");
            Multa multa = new Multa();
            multa.setViagem(v); multa.setChaveExterna(chave); multa.setValor(valor);
            multa.setOcorridoEm(item.ocorridoEm().atOffset(ZoneOffset.UTC).toLocalDateTime());
            multa.setMotivo("Registrada pelo agente LK");
            multa.setOrigem(EventoViagem.Origem.TELEMETRIA);
            eventos.saveAndFlush(multa);
            if (!v.getEventos().contains(multa)) v.getEventos().add(multa);
            if (v.getStatus() == StatusViagem.CONCLUIDA)
                cnhs.cobrarMultaAtrasada(v);
            if (v.getPagamento() != null)
                v.setPendenciaMultas("Multa recebida após o acerto. Conferir despesas; o pagamento anterior não foi alterado.");
            recalcular(v);
            confirmadas.add(item.id());
        }
        return confirmadas;
    }

    /** Total definitivo do job. Nunca soma o total inteiro em cima das multas já capturadas. */
    @Transactional
    public void conferir(Viagem v, BigDecimal total) {
        usuarios.bloquear(v.getMotorista().getId()).orElseThrow();
        if (total == null) {
            if (v.getMultasVtlog() == null)
                v.setPendenciaMultas(SEM_TOTAL);
            return;
        }
        BigDecimal validado = dinheiro(total);
        if (v.getMultasVtlog() != null) {
            if (v.getMultasVtlog().compareTo(validado) != 0)
                v.setPendenciaMultas("VTLog reenviou a entrega com outro total de multas. Conferir antes do acerto.");
            return;
        }
        v.setMultasVtlog(validado);
        if (AGUARDANDO.equals(v.getPendenciaMultas()) || SEM_TOTAL.equals(v.getPendenciaMultas()))
            v.setPendenciaMultas(null);
        if (v.getPagamento() != null)
            v.setPendenciaMultas("Conferência VTLog recebida após o acerto. O pagamento anterior não foi alterado.");
        recalcular(v);
    }

    private void recalcular(Viagem v) {
        if (v.getMultasVtlog() == null) return;
        List<EventoViagem> lista = eventos.findByViagemId(v.getId());
        BigDecimal registrado = lista.stream().filter(e -> e instanceof Multa m && !m.isAjusteVtlog())
            .map(EventoViagem::getValor).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diferenca = v.getMultasVtlog().subtract(registrado);
        String chave = "vtlog-ajuste:" + v.getId();
        Multa ajuste = lista.stream().filter(e -> chave.equals(e.getChaveExterna())).map(Multa.class::cast).findFirst().orElse(null);
        if (diferenca.signum() > 0 || ajuste != null) {
            if (ajuste == null) {
                ajuste = new Multa(); ajuste.setViagem(v); ajuste.setChaveExterna(chave);
                ajuste.setAjusteVtlog(true); ajuste.setOrigem(EventoViagem.Origem.TELEMETRIA);
                ajuste.setMotivo("Ajuste de conferência VTLog — job " + v.getVtlogJobId());
            }
            ajuste.setValor(diferenca.max(BigDecimal.ZERO));
            ajuste.setObservacao("Complemento do total informado pelo VTLog. Não representa uma infração individual nem desconta pontos de CNH.");
            eventos.saveAndFlush(ajuste);
            if (!v.getEventos().contains(ajuste)) v.getEventos().add(ajuste);
        }
        if (diferenca.signum() < 0)
            v.setPendenciaMultas("Multas registradas (" + registrado + ") excedem o total VTLog (" + v.getMultasVtlog() + "). Conferir possível duplicidade.");
        viagens.save(v);
    }

    @Transactional
    public void confirmar(UUID id, Usuario gestor, String observacao) {
        Viagem inicial = viagens.findById(id).orElseThrow();
        usuarios.bloquear(inicial.getMotorista().getId()).orElseThrow();
        if (observacao == null || observacao.isBlank() || observacao.length() > 500)
            throw new IllegalArgumentException("Descreva a conferência em até 500 caracteres.");
        if (inicial.getPendenciaMultas() == null) return;
        Ocorrencia auditoria = new Ocorrencia(); auditoria.setViagem(inicial);
        auditoria.setTitulo("Conferência de multas aprovada por " + gestor.getNome());
        auditoria.setDescricao(observacao); eventos.save(auditoria);
        inicial.setPendenciaMultas(null); viagens.save(inicial);
    }
}
