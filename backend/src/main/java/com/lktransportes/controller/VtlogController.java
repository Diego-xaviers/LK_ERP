package com.lktransportes.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lktransportes.model.Viagem;
import com.lktransportes.service.VtlogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recebe entregas registradas pelo bot do Discord que monitora o #registro-vtlog.
 * Autenticação via X-Vtlog-Secret (segredo compartilhado, não JWT).
 *
 * Também recebe snapshots ao vivo via webhook do VTLog (live.snapshot),
 * assinados com HMAC-SHA256 no cabeçalho X-VTLog-Signature.
 */
@RestController
@RequestMapping("/api/vtlog")
public class VtlogController {

    private static final Logger log = LoggerFactory.getLogger(VtlogController.class);

    private final VtlogService vtlog;
    private final ObjectMapper mapper;
    private final com.lktransportes.service.VtlogJobCache jobCache;
    private final com.lktransportes.service.VtlogWebhook webhook;

    // Cache em memória do último snapshot recebido pelo webhook do VTLog.
    private volatile String snapshotJson = null;
    private volatile Instant snapshotAtualizado = null;


    public VtlogController(VtlogService vtlog, ObjectMapper mapper,
                           com.lktransportes.service.VtlogJobCache jobCache,
                           com.lktransportes.service.VtlogWebhook webhook) {
        this.vtlog = vtlog;
        this.mapper = mapper;
        this.jobCache = jobCache;
        this.webhook = webhook;
    }

    @PostMapping("/entrega")
    public ResponseEntity<?> entrega(
            @RequestHeader(value = "X-Vtlog-Secret", required = false) String segredo,
            @RequestBody EntregaRequest req) {
        try {
            vtlog.validarSegredo(segredo);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", e.getMessage()));
        }

        try {
            Viagem v = vtlog.registrarEntrega(new VtlogService.EntregaVtlog(
                    req.job_id, req.steam_id,
                    req.origem, req.destino,
                    req.empresa_origem, req.empresa_destino,
                    req.carga, req.peso_kg,
                    req.distancia_km, req.combustivel_gasto_l, req.dano_pct,
                    req.valor_frete, req.total_multas, req.inicio_epoch_ms, req.fim_epoch_ms,
                    req.total_combustivel, req.litros_combustivel, req.preco_combustivel,
                    req.total_manutencao, req.detalhe_manutencao,
                    req.pedagios == null ? java.util.List.of() : req.pedagios.stream()
                            .map(p -> new VtlogService.EventoVtlog(p.id, p.valor, p.ocorrido_epoch_ms, p.detalhe)).toList()
            ));
            return ResponseEntity.ok(Map.of(
                    "viagem", v.getNumero(),
                    "mensagem", "Entrega do job " + req.job_id + " registrada como viagem #" + v.getNumero() + "."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
        }
    }

    /** Recebe o snapshot ao vivo do VTLog via webhook (event: live.snapshot). */
    @PostMapping("/live-snapshot")
    public ResponseEntity<?> liveSnapshot(@RequestBody byte[] corpo,
            @RequestHeader(value = "X-VTLog-Signature", required = false) String assinatura) {
        webhook.validar(corpo, assinatura);
        String payload = new String(corpo, java.nio.charset.StandardCharsets.UTF_8);
        try { mapper.readTree(payload); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("erro", "JSON inválido.")); }
        snapshotJson = payload;
        snapshotAtualizado = Instant.now();
        // Ao vivo é informativo; despesas vêm dos recibos e do total definitivo da entrega.
        atualizarCacheJobs(payload);
        return ResponseEntity.ok(Map.of("ok", true));
    }


    /**
     * Extrai dados do job de cada driver no snapshot e salva no VtlogJobCache.
     * Tenta vários nomes de campo para compatibilidade com versões do VTLog.
     */
    private void atualizarCacheJobs(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode drivers = root.path("drivers");
            if (drivers.isMissingNode()) drivers = root.path("data");
            if (!drivers.isArray()) return;

            for (JsonNode d : drivers) {
                String steamId = nomeOuNulo(d, "steam_id", "steamId");
                if (steamId == null) continue;

                boolean onJob = boolDeNode(d, "on_job", "is_on_job", "onJob");
                if (!onJob) {
                    jobCache.atualizar(steamId, null); // saiu de serviço
                    continue;
                }

                String jobId    = nomeOuNulo(d, "job_id", "current_job", "jobId", "id");
                String cargo    = nomeOuNulo(d, "cargo", "cargo_name", "cargo_id");
                String origem   = nomeOuNulo(d, "source_city", "origin_city", "job_origin",    "source_city_real_name");
                String destino  = nomeOuNulo(d, "destination_city", "dest_city", "job_destination", "destination_city_real_name");
                String empOrig  = nomeOuNulo(d, "source_company", "origin_company",  "source_company_name");
                String empDest  = nomeOuNulo(d, "destination_company", "dest_company", "destination_company_name");
                Double massa    = numDeNode(d, "cargo_mass", "cargo_weight", "mass");
                Integer dist    = intDeNode(d, "planned_distance", "job_distance", "distance");

                jobCache.atualizar(steamId, new com.lktransportes.service.VtlogJobCache.DadosJob(
                        jobId, cargo, origem, destino, empOrig, empDest, massa, dist));
            }
        } catch (Exception ignored) {}
    }

    private boolean boolDeNode(JsonNode d, String... campos) {
        for (String c : campos) {
            JsonNode v = d.path(c);
            if (!v.isMissingNode() && v.isBoolean()) return v.asBoolean();
        }
        return false;
    }

    private Double numDeNode(JsonNode d, String... campos) {
        for (String c : campos) {
            JsonNode v = d.path(c);
            if (!v.isMissingNode() && v.isNumber()) return v.asDouble();
        }
        return null;
    }

    private Integer intDeNode(JsonNode d, String... campos) {
        Double v = numDeNode(d, campos);
        return v != null ? (int) Math.round(v) : null;
    }

    private String nomeOuNulo(JsonNode node, String... campos) {
        for (String c : campos) {
            JsonNode v = node.path(c);
            if (!v.isMissingNode() && v.isTextual()) return v.asText();
        }
        return null;
    }

    /** Retorna o último snapshot ao vivo para o frontend. Exige JWT. */
    @GetMapping("/live")
    public ResponseEntity<?> live() {
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("online", snapshotJson != null);
        resp.put("atualizado", snapshotAtualizado != null ? snapshotAtualizado.toString() : null);
        resp.put("snapshot", snapshotJson);
        return ResponseEntity.ok(resp);
    }

    record EntregaRequest(
            String job_id,
            String steam_id,
            String origem,
            String destino,
            String empresa_origem,
            String empresa_destino,
            String carga,
            BigDecimal peso_kg,
            Double distancia_km,
            Double combustivel_gasto_l,
            Double dano_pct,
            BigDecimal valor_frete,
            BigDecimal total_multas,
            Long inicio_epoch_ms,
            Long fim_epoch_ms,
            BigDecimal total_combustivel,
            Double litros_combustivel,
            BigDecimal preco_combustivel,
            BigDecimal total_manutencao,
            String detalhe_manutencao,
            java.util.List<EventoRequest> pedagios
    ) {}

    record EventoRequest(String id, BigDecimal valor, Long ocorrido_epoch_ms, String detalhe) {}
}
