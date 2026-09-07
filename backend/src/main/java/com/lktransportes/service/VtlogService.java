package com.lktransportes.service;

import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class VtlogService {

    private final PerfilRepository perfis;
    private final ViagemRepository viagens;
    private final TelemetriaViagemRepository telemetrias;
    private final EventoViagemRepository eventos;
    private final ViagemService viagemService;
    private final MultasService multas;
    private final UsuarioRepository usuarios;

    @Value("${lk.vtlog-secret:}")
    private String vtlogSecret;

    public VtlogService(PerfilRepository perfis, ViagemRepository viagens,
                        TelemetriaViagemRepository telemetrias, EventoViagemRepository eventos,
                        ViagemService viagemService, MultasService multas, UsuarioRepository usuarios) {
        this.perfis = perfis;
        this.viagens = viagens;
        this.telemetrias = telemetrias;
        this.eventos = eventos;
        this.viagemService = viagemService;
        this.multas = multas;
        this.usuarios = usuarios;
    }


    public void validarSegredo(String cabecalho) {
        if (vtlogSecret.isBlank() || !vtlogSecret.equals(cabecalho)) {
            throw new SecurityException("Segredo inválido.");
        }
    }

    @Transactional
    public Viagem registrarEntrega(EntregaVtlog req) {
        if (req.jobId() == null || !req.jobId().matches("[0-9]{1,20}"))
            throw new IllegalArgumentException("Job VTLog inválido.");
        if (req.totalMultas() != null) MultasService.dinheiro(req.totalMultas());
        Perfil perfil = perfis.findBySteamId(req.steamId())
                .orElseThrow(() -> new IllegalArgumentException("Steam ID não encontrado. Cadastre no perfil."));
        Usuario motorista = perfil.getUsuario();
        usuarios.bloquear(motorista.getId()).orElseThrow();
        // Idempotência: job já registrado retorna a viagem existente
        Optional<Viagem> existentePorJob = viagens.findByVtlogJobId(req.jobId());
        if (existentePorJob.isPresent()) {
            Viagem v = existentePorJob.get();
            if (!v.getMotorista().getId().equals(motorista.getId()))
                throw new IllegalArgumentException("Job vinculado a outro motorista.");
            multas.conferir(v, req.totalMultas());
            if (v.getStatus() == StatusViagem.EM_ANDAMENTO) return concluirViagemAtiva(v, req);
            return v;
        }
        // Não associa um job antigo automaticamente à carga que estiver ativa agora.
        var mesmaRota = viagens.findByMotoristaIdOrderByCriadaEmDesc(motorista.getId()).stream()
            .filter(v -> v.getVtlogJobId() == null && v.getStatus() != StatusViagem.CRIADA)
            .filter(v -> igual(v.getOrigem(), req.origem()) && igual(v.getDestino(), req.destino()) && igual(v.getCarga(), req.carga()))
            .toList();
        var candidatas = mesmaRota.stream().filter(v -> correspondeAoHorario(v, req)).toList();
        if (candidatas.isEmpty() && !mesmaRota.isEmpty())
            throw new IllegalStateException("Job sem correspondência segura de horário. Conferir vínculo com a viagem.");
        if (candidatas.size() > 1)
            throw new IllegalStateException("Mais de uma viagem compatível com o job. É necessário conferir o vínculo.");
        if (candidatas.size() == 1) {
            Viagem v = candidatas.getFirst();
            v.setVtlogJobId(req.jobId());
            multas.conferir(v, req.totalMultas());
            if (v.getStatus() == StatusViagem.EM_ANDAMENTO) return concluirViagemAtiva(v, req);
            return viagens.save(v);
        }

        // Sem viagem ativa: cria do zero (entrega não precedida de agente PS1)
        return criarViagemConcluida(motorista, req);
    }

    private boolean igual(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.strip().equalsIgnoreCase(b.strip());
    }

    private boolean correspondeAoHorario(Viagem v, EntregaVtlog req) {
        if (req.inicioEpochMs() == null || req.fimEpochMs() == null || v.getIniciadaEm() == null) return false;
        if (req.inicioEpochMs() > req.fimEpochMs()) return false;
        var inicio = java.time.Instant.ofEpochMilli(req.inicioEpochMs()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        var fim = java.time.Instant.ofEpochMilli(req.fimEpochMs()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        return !v.getIniciadaEm().isBefore(inicio.minusMinutes(5)) && !v.getIniciadaEm().isAfter(fim.plusMinutes(5));
    }

    /**
     * Enriquece a viagem EM_ANDAMENTO com os dados definitivos do VTLog e
     * passa pelo caminho completo de finalização (conferência, crédito, etc.).
     */
    private Viagem concluirViagemAtiva(Viagem v, EntregaVtlog req) {
        // Vincula ao job VTLog para garantir idempotência futura
        v.setVtlogJobId(req.jobId());

        // Preenche campos ausentes com o que o VTLog sabe
        if (req.valorFrete() != null && (v.getValorFrete() == null || v.getValorFrete().signum() == 0)) {
            v.setValorFrete(req.valorFrete());
        }
        if (req.pesoKg() != null && (v.getPesoKg() == null || v.getPesoKg().signum() == 0)) {
            v.setPesoKg(req.pesoKg());
        }
        if (req.origem() != null && !req.origem().isBlank() && (v.getOrigem() == null || v.getOrigem().equals("—"))) {
            v.setOrigem(req.origem());
        }
        if (req.destino() != null && !req.destino().isBlank() && (v.getDestino() == null || v.getDestino().equals("—"))) {
            v.setDestino(req.destino());
        }
        if (req.empresaOrigem() != null && !req.empresaOrigem().isBlank() && "—".equals(v.getEmpresaRemetente())) {
            v.setEmpresaRemetente(req.empresaOrigem());
        }
        if (req.empresaDestino() != null && !req.empresaDestino().isBlank() && "—".equals(v.getEmpresaDestinataria())) {
            v.setEmpresaDestinataria(req.empresaDestino());
        }
        if (req.carga() != null && !req.carga().isBlank() && (v.getCarga() == null || v.getCarga().isBlank())) {
            v.setCarga(req.carga());
        }
        viagens.save(v);

        // Enriquece a TelemetriaViagem com a distância real e dados de consumo do VTLog
        telemetrias.findByViagemId(v.getId()).ifPresent(tv -> {
            if (req.distanciaKm() != null && tv.getDistanciaConfirmadaKm() == null) {
                tv.setDistanciaConfirmadaKm(req.distanciaKm());
            }
            if (req.danoPct() != null) {
                tv.setDanoAtualPct(req.danoPct());
                if (tv.getDanoRegistradoPct() == null) tv.setDanoRegistradoPct(req.danoPct());
            }
            telemetrias.save(tv);
        });

        multas.conferir(v, req.totalMultas());
        // Finaliza pelo caminho completo: conferência, crédito de frete, etc.
        viagemService.finalizar(v.getId(), null, null);

        return viagens.findById(v.getId()).orElse(v);
    }

    /**
     * Cria uma viagem já concluída quando não havia viagem ativa no momento da entrega.
     * (Motorista não usou o agente PS1 ou entregou antes do ping ser processado.)
     */
    private Viagem criarViagemConcluida(Usuario motorista, EntregaVtlog req) {
        Caminhao caminhao = viagens.findFirstByMotoristaIdOrderByNumeroDesc(motorista.getId())
                .map(Viagem::getCaminhao)
                .orElseThrow(() -> new IllegalStateException(
                        "Motorista não tem viagem anterior. Cadastre uma viagem manual primeiro para vincular o caminhão."));

        Viagem v = new Viagem();
        v.setNumero(viagens.ultimoNumero() + 1);
        v.setOrigem(req.origem() != null ? req.origem() : "—");
        v.setDestino(req.destino() != null ? req.destino() : "—");
        v.setEmpresaRemetente(req.empresaOrigem() != null ? req.empresaOrigem() : "—");
        v.setEmpresaDestinataria(req.empresaDestino() != null ? req.empresaDestino() : "—");
        v.setCarga(req.carga() != null ? req.carga() : "—");
        v.setPesoKg(req.pesoKg() != null ? req.pesoKg() : BigDecimal.ZERO);
        v.setValorFrete(req.valorFrete());
        v.setMotorista(motorista);
        v.setCaminhao(caminhao);
        v.setVtlogJobId(req.jobId());
        // Inicia direto para poder chamar finalizar() pelo caminho completo
        v.iniciar();
        viagens.save(v);

        TelemetriaViagem tel = new TelemetriaViagem();
        tel.setViagem(v);
        tel.setDistanciaConfirmadaKm(req.distanciaKm());
        tel.setLitrosAbastecidos(req.combustivelGastoL() != null ? req.combustivelGastoL() : 0d);
        tel.setDanoAtualPct(req.danoPct());
        tel.setDanoRegistradoPct(req.danoPct());
        telemetrias.save(tel);

        multas.conferir(v, req.totalMultas());

        viagemService.finalizar(v.getId(), null, null);

        return viagens.findById(v.getId()).orElse(v);
    }

    public record EntregaVtlog(
            String jobId,
            String steamId,
            String origem,
            String destino,
            String empresaOrigem,
            String empresaDestino,
            String carga,
            BigDecimal pesoKg,
            Double distanciaKm,
            Double combustivelGastoL,
            Double danoPct,
            BigDecimal valorFrete,
            BigDecimal totalMultas,
            Long inicioEpochMs,
            Long fimEpochMs
    ) {}
}
