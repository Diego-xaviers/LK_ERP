package com.lktransportes.controller;

import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** CRUDs simples dos cadastros administrativos. */
@RestController
@RequestMapping("/api")
public class CadastrosController {

    private final PostoRepository postos;
    private final OficinaRepository oficinas;
    private final CarretaRepository carretas;
    private final EmpresaParceiraRepository empresas;
    private final AvisoRepository avisos;
    private final CaminhaoRepository caminhoes;
    private final UsuarioRepository usuarios;

    public CadastrosController(PostoRepository postos, OficinaRepository oficinas,
                               CarretaRepository carretas, EmpresaParceiraRepository empresas,
                               AvisoRepository avisos, CaminhaoRepository caminhoes,
                               UsuarioRepository usuarios) {
        this.postos = postos; this.oficinas = oficinas; this.carretas = carretas;
        this.empresas = empresas; this.avisos = avisos; this.caminhoes = caminhoes;
        this.usuarios = usuarios;
    }

    @GetMapping("/postos") public List<Posto> listarPostos() { return postos.findAll(); }
    @PostMapping("/postos") public Posto criarPosto(@RequestBody Posto p) { return postos.save(p); }
    @DeleteMapping("/postos/{id}") public void removerPosto(@PathVariable UUID id) { postos.deleteById(id); }

    @GetMapping("/oficinas") public List<Oficina> listarOficinas() { return oficinas.findAll(); }
    @PostMapping("/oficinas") public Oficina criarOficina(@RequestBody Oficina o) { return oficinas.save(o); }
    @DeleteMapping("/oficinas/{id}") public void removerOficina(@PathVariable UUID id) { oficinas.deleteById(id); }

    @GetMapping("/carretas") public List<Carreta> listarCarretas() { return carretas.findAll(); }
    @PostMapping("/carretas") public Carreta criarCarreta(@RequestBody Carreta c) { return carretas.save(c); }
    @DeleteMapping("/carretas/{id}") public void removerCarreta(@PathVariable UUID id) { carretas.deleteById(id); }

    @GetMapping("/empresas") public List<EmpresaParceira> listarEmpresas() { return empresas.findAll(); }
    @PostMapping("/empresas") public EmpresaParceira criarEmpresa(@RequestBody EmpresaParceira e) { return empresas.save(e); }
    @DeleteMapping("/empresas/{id}") public void removerEmpresa(@PathVariable UUID id) { empresas.deleteById(id); }

    @GetMapping("/avisos")
    public List<Aviso> listarAvisos() {
        return avisos.findAll().stream().filter(Aviso::estaVigente).toList();
    }
    @PostMapping("/avisos") public Aviso criarAviso(@RequestBody Aviso a) { return avisos.save(a); }
    @DeleteMapping("/avisos/{id}") public void removerAviso(@PathVariable UUID id) { avisos.deleteById(id); }

    @GetMapping("/caminhoes") public List<Caminhao> listarCaminhoes() { return caminhoes.findAll(); }
    @PostMapping("/caminhoes") public Caminhao criarCaminhao(@RequestBody Caminhao c) { return caminhoes.save(c); }
    @DeleteMapping("/caminhoes/{id}") public void removerCaminhao(@PathVariable UUID id) { caminhoes.deleteById(id); }

    /**
     * Define o dono do caminhão. Sem dono ele volta a ser da empresa e qualquer
     * motorista usa; com dono, só ele.
     */
    @PostMapping("/caminhoes/{id}/dono")
    public Caminhao definirDono(@PathVariable UUID id, @RequestBody java.util.Map<String, String> corpo) {
        Caminhao c = caminhoes.findById(id).orElseThrow();
        String motoristaId = corpo.get("motoristaId");
        c.setDono(motoristaId == null || motoristaId.isBlank()
                ? null : usuarios.findById(UUID.fromString(motoristaId)).orElseThrow());
        return caminhoes.save(c);
    }
}
