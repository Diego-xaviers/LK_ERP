package com.lktransportes.controller;

import com.lktransportes.model.Viagem;
import com.lktransportes.service.VtlogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Recebe entregas registradas pelo bot do Discord que monitora o #registro-vtlog.
 * Autenticação via X-Vtlog-Secret (segredo compartilhado, não JWT).
 */
@RestController
@RequestMapping("/api/vtlog")
public class VtlogController {

    private final VtlogService vtlog;

    public VtlogController(VtlogService vtlog) {
        this.vtlog = vtlog;
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
