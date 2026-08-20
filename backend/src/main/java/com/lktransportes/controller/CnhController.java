package com.lktransportes.controller;

import com.lktransportes.model.Cnh;
import com.lktransportes.model.Perfil;
import com.lktransportes.model.Usuario;
import com.lktransportes.repository.PerfilRepository;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.security.SessaoAtual;
import com.lktransportes.service.CnhService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cnh")
public class CnhController {

    private final CnhService service;
    private final UsuarioRepository usuarios;
    private final PerfilRepository perfis;
    private final SessaoAtual sessao;

    public CnhController(CnhService service, UsuarioRepository usuarios,
                         PerfilRepository perfis, SessaoAtual sessao) {
        this.service = service;
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.sessao = sessao;
    }

    /** A CNH com os dados do perfil embutidos — é o que a tela desenha. */
    @GetMapping("/{motoristaId}")
    public ResponseEntity<Map<String, Object>> buscar(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        return service.de(motoristaId)
                .map(c -> ResponseEntity.ok(comoMapa(c, motoristaId)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{motoristaId}/emitir")
    public Map<String, Object> emitir(@PathVariable UUID motoristaId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        sessao.exigirGestor();
        String categoria = body == null ? null : (String) body.get("categoria");
        String validade = body == null ? null : (String) body.get("validade");
        Cnh c = service.emitir(motoristaId, categoria,
                validade == null || validade.isBlank() ? null : LocalDate.parse(validade),
                gestor());
        return comoMapa(c, motoristaId);
    }

    @PostMapping("/{motoristaId}/reabilitar")
    public Map<String, Object> reabilitar(@PathVariable UUID motoristaId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        sessao.exigirGestor();
        String obs = body == null ? null : (String) body.get("observacao");
        return comoMapa(service.reabilitar(motoristaId, gestor(), obs), motoristaId);
    }

    @PostMapping("/{motoristaId}/suspender")
    public Map<String, Object> suspender(@PathVariable UUID motoristaId,
                                         @RequestBody(required = false) Map<String, Object> body) {
        sessao.exigirGestor();
        String obs = body == null ? null : (String) body.get("observacao");
        return comoMapa(service.suspender(motoristaId, gestor(), obs), motoristaId);
    }

    private Usuario gestor() {
        return usuarios.findById(sessao.id()).orElseThrow();
    }

    private Map<String, Object> comoMapa(Cnh c, UUID motoristaId) {
        Usuario m = usuarios.findById(motoristaId).orElseThrow();
        Perfil p = perfis.findByUsuarioId(motoristaId).orElse(null);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("numeroRegistro", c.getNumeroRegistro());
        r.put("categoria", c.getCategoria());
        r.put("primeiraHabilitacao", c.getPrimeiraHabilitacao());
        r.put("validade", c.getValidade());
        r.put("pontos", c.getPontos());
        r.put("pontosIniciais", Cnh.PONTOS_INICIAIS);
        r.put("estado", c.estado());
        r.put("valida", c.valida());
        r.put("motivoBloqueio", c.motivoDoBloqueio());
        r.put("emitidaEm", c.getEmitidaEm());
        r.put("emitidaPor", c.getEmitidaPor() == null ? null : c.getEmitidaPor().getNome());
        r.put("observacoes", c.getObservacoes());

        // Vem do perfil: a CNH não guarda cópia desses dados, para não divergir.
        r.put("nome", p != null && p.getNomeCompleto() != null ? p.getNomeCompleto() : m.getNome());
        r.put("dataNascimento", p == null ? null : p.getDataNascimento());
        r.put("cpf", p == null ? null : p.getCpf());
        r.put("rg", p == null ? null : p.getRg());
        r.put("orgaoEmissor", p == null ? null : p.getOrgaoEmissor());
        r.put("ufEmissor", p == null ? null : p.getUfEmissor());
        r.put("nomeMae", p == null ? null : p.getNomeMae());
        r.put("nomePai", p == null ? null : p.getNomePai());
        r.put("naturalidade", p == null ? null : p.getNaturalidadeCidade());
        r.put("naturalidadeUf", p == null ? null : p.getNaturalidadeUf());
        r.put("fotoBase64", p == null ? null : p.getFotoBase64());
        r.put("assinaturaBase64", p == null ? null : p.getAssinaturaBase64());
        return r;
    }
}
