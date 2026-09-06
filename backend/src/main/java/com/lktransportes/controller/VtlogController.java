package com.lktransportes.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lktransportes.model.Viagem;
import com.lktransportes.service.VtlogService;
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

    private final VtlogService vtlog;
    private final ObjectMapper mapper;

    // Cache em memória do último snapshot recebido pelo webhook do VTLog.
    private volatile String snapshotJson = null;
    private volatile Instant snapshotAtualizado = null;

    /** Último valor de fines conhecido por steam_id — para calcular deltas. */
    private final ConcurrentHashMap<String, Double> multasAnteriores = new ConcurrentHashMap<>();

    public VtlogController(VtlogService vtlog, ObjectMapper mapper) {
        this.vtlog = vtlog;
        this.mapper = mapper;
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
        snapshotJson = payload;
        snapshotAtualizado = Instant.now();
        detectarMultas(payload);
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
        if (snapshotJson == null) {
            return ResponseEntity.ok(Map.of(
                "motoristas", java.util.List.of(),
                "atualizado", (Object) null,
                "online", false
            ));
        }
        return ResponseEntity.ok(Map.of(
            "snapshot", snapshotJson,
            "atualizado", snapshotAtualizado.toString(),
            "online", true
        ));
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
