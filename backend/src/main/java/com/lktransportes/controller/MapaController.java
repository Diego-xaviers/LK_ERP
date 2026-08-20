package com.lktransportes.controller;

import com.lktransportes.model.CidadeMapa;
import com.lktransportes.model.MapaConhecido;
import com.lktransportes.security.SessaoAtual;
import com.lktransportes.service.MapaService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mapa")
public class MapaController {

    private final MapaService service;
    private final SessaoAtual sessao;

    public MapaController(MapaService service, SessaoAtual sessao) {
        this.service = service;
        this.sessao = sessao;
    }

    @GetMapping
    public Map<String, Object> painel() {
        sessao.exigirGestor();
        MapaConhecido m = service.config();
        List<CidadeMapa> cidades = service.cidadesConhecidas();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("modo", m.getModo().name());
        r.put("temArea", m.temAreaDefinida());
        r.put("minX", m.getMinX());
        r.put("maxX", m.getMaxX());
        r.put("minZ", m.getMinZ());
        r.put("maxZ", m.getMaxZ());
        r.put("margemMetros", m.getMargemMetros());
        r.put("cidades", cidades.stream().map(c -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", c.getId());
            x.put("idJogo", c.getIdJogo());
            x.put("nome", c.getNome());
            x.put("vezesVista", c.getVezesVista());
            return x;
        }).toList());
        return r;
    }

    @PostMapping("/trancar")
    public Map<String, Object> trancar() {
        sessao.exigirGestor();
        service.trancar();
        return painel();
    }

    @PostMapping("/aprender")
    public Map<String, Object> aprender() {
        sessao.exigirGestor();
        service.voltarAAprender();
        return painel();
    }

    @DeleteMapping("/cidades/{id}")
    public Map<String, Object> esquecer(@PathVariable UUID id) {
        sessao.exigirGestor();
        service.esquecerCidade(id);
        return painel();
    }

    @PostMapping("/margem")
    public Map<String, Object> margem(@RequestBody Map<String, Object> body) {
        sessao.exigirGestor();
        service.definirMargem(Double.valueOf(String.valueOf(body.get("metros"))));
        return painel();
    }
}
