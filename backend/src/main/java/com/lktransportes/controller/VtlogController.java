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

    // Cache em memória do último snapshot recebido pelo webhook do VTLog.
    private volatile String snapshotJson = null;
    private volatile Instant snapshotAtualizado = null;

    /** Último valor de fines conhecido por steam_id — para calcular deltas. */
    private final ConcurrentHashMap<String, Double> multasAnteriores = new ConcurrentHashMap<>();

    /** Último valor de toll pago por steam_id — para calcular deltas. */
    private final ConcurrentHashMap<String, Double> pedagiosAnteriores = new ConcurrentHashMap<>();

    public VtlogController(VtlogService vtlog, ObjectMapper mapper,
                           com.lktransportes.service.VtlogJobCache jobCache) {
        this.vtlog = vtlog;
        this.mapper = mapper;
        this.jobCache = jobCache;
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
                    req.valor_frete
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
    public ResponseEntity<?> liveSnapshot(@RequestBody String payload) {
        log.info("[VTLog] live-snapshot recebido: {}", payload);
        snapshotJson = payload;
        snapshotAtualizado = Instant.now();
        detectarMultas(payload);
        detectarPedagios(payload);
        atualizarCacheJobs(payload);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Percorre o snapshot procurando `expense_fines` por driver.
     * Quando o valor aumenta em relação ao anterior, registra uma Multa.
     * Compatível com o formato VTLog: { drivers: [ { steam_id, economy: { expense_fines } } ] }
     * ou variações com campo direto `fines` ou `expense_fines` no nível do driver.
     */
    private void detectarMultas(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode drivers = root.path("drivers");
            if (drivers.isMissingNode()) drivers = root.path("data");
            if (!drivers.isArray()) return;

            for (JsonNode d : drivers) {
                String steamId = nomeOuNulo(d, "steam_id", "steamId");
                if (steamId == null) continue;

                double finesAtual = finesDeNode(d);
                if (finesAtual < 0) continue;

                Double anterior = multasAnteriores.put(steamId, finesAtual);
                if (anterior != null && finesAtual > anterior) {
                    double delta = finesAtual - anterior;
                    vtlog.registrarMultaVtlog(steamId, delta);
                }
            }
        } catch (Exception ignored) {
            // Payload malformado não deve derrubar o endpoint.
        }
    }

    /**
     * Percorre o snapshot procurando `toll_paid`/`expense_toll` por driver.
     * Quando o valor acumulado aumenta, registra um Pedagio.
     * Compatível com variações de nomes de campo do VTLog.
     */
    private void detectarPedagios(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode drivers = root.path("drivers");
            if (drivers.isMissingNode()) drivers = root.path("data");
            if (!drivers.isArray()) return;

            for (JsonNode d : drivers) {
                String steamId = nomeOuNulo(d, "steam_id", "steamId");
                if (steamId == null) continue;

                double tollAtual = tollDeNode(d);
                if (tollAtual < 0) continue;

                Double anterior = pedagiosAnteriores.put(steamId, tollAtual);
                if (anterior != null && tollAtual > anterior) {
                    double delta = tollAtual - anterior;
                    vtlog.registrarPedagioVtlog(steamId, delta);
                }
            }
        } catch (Exception ignored) {
            // Payload malformado não deve derrubar o endpoint.
        }
    }

    private double tollDeNode(JsonNode d) {
        for (String campo : new String[]{"toll_paid", "tolls", "expense_toll", "toll"}) {
            JsonNode v = d.path(campo);
            if (!v.isMissingNode() && v.isNumber()) return v.asDouble();
        }
        JsonNode eco = d.path("economy");
        if (!eco.isMissingNode()) {
            for (String campo : new String[]{"toll_paid", "tolls", "expense_toll", "toll"}) {
                JsonNode v = eco.path(campo);
                if (!v.isMissingNode() && v.isNumber()) return v.asDouble();
            }
        }
        return -1;
    }

    private double finesDeNode(JsonNode d) {
        // Tenta vários caminhos conhecidos do VTLog
        for (String campo : new String[]{"expense_fines", "fines"}) {
            JsonNode v = d.path(campo);
            if (!v.isMissingNode() && v.isNumber()) return v.asDouble();
        }
        // economia aninhada
        JsonNode eco = d.path("economy");
        if (!eco.isMissingNode()) {
            for (String campo : new String[]{"expense_fines", "fines"}) {
                JsonNode v = eco.path(campo);
                if (!v.isMissingNode() && v.isNumber()) return v.asDouble();
            }
        }
        return -1;
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
            BigDecimal valor_frete
    ) {}
}
