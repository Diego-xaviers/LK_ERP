package com.lktransportes.controller;

import com.lktransportes.dto.AceitarDemandaRequest;
import com.lktransportes.dto.DemandaResponse;
import com.lktransportes.dto.NovaDemandaRequest;
import com.lktransportes.dto.ViagemResponse;
import com.lktransportes.security.SessaoAtual;
import com.lktransportes.service.DemandaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/demandas")
public class DemandaController {

    private final DemandaService service;
    private final SessaoAtual sessao;

    public DemandaController(DemandaService service, SessaoAtual sessao) {
        this.service = service;
        this.sessao = sessao;
    }

    /** Quadro completo, inclusive concluídas e canceladas — só gestor. */
    @GetMapping
    public List<DemandaResponse> listar() {
        sessao.exigirGestor();
        return service.listar();
    }

    /** O que está disponível para pegar. Todo motorista logado vê. */
    @GetMapping("/abertas")
    public List<DemandaResponse> abertas() {
        return service.abertas();
    }

    @GetMapping("/{id}")
    public DemandaResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PostMapping
    public DemandaResponse criar(@Valid @RequestBody NovaDemandaRequest req) {
        sessao.exigirGestor();
        return service.criar(req);
    }

    @PostMapping("/{id}/cancelar")
    public DemandaResponse cancelar(@PathVariable UUID id) {
        sessao.exigirGestor();
        return service.cancelar(id);
    }

    /** O motorista pega a carga para si — sempre para si, nunca para outro. */
    @PostMapping("/{id}/aceitar")
    public ViagemResponse aceitar(@PathVariable UUID id, @Valid @RequestBody AceitarDemandaRequest req) {
        return service.aceitar(id, sessao.id(), req);
    }
}
