package com.lktransportes.controller;

import com.lktransportes.dto.PerfilRequest;
import com.lktransportes.model.Perfil;
import com.lktransportes.repository.PerfilRepository;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.security.SessaoAtual;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    /** Foto e assinatura vêm em base64; acima disso é imagem sem redimensionar. */
    private static final int LIMITE_IMAGEM = 700_000;

    private final PerfilRepository perfis;
    private final UsuarioRepository usuarios;
    private final SessaoAtual sessao;

    public PerfilController(PerfilRepository perfis, UsuarioRepository usuarios, SessaoAtual sessao) {
        this.perfis = perfis;
        this.usuarios = usuarios;
        this.sessao = sessao;
    }

    @GetMapping("/{usuarioId}")
    @Transactional
    public Map<String, Object> buscar(@PathVariable UUID usuarioId) {
        sessao.exigirDonoOuGestor(usuarioId);
        return comoMapa(obterOuCriar(usuarioId));
    }

    @PutMapping("/{usuarioId}")
    @Transactional
    public Map<String, Object> salvar(@PathVariable UUID usuarioId, @RequestBody PerfilRequest req) {
        sessao.exigirDonoOuGestor(usuarioId);
        Perfil p = obterOuCriar(usuarioId);

        p.setNomeCompleto(req.nomeCompleto);
        p.setDataNascimento(req.dataNascimento);
        p.setCpf(req.cpf);
        p.setRg(req.rg);
        p.setOrgaoEmissor(req.orgaoEmissor);
        p.setUfEmissor(maiuscula(req.ufEmissor));
        p.setNomeMae(req.nomeMae);
        p.setNomePai(req.nomePai);
        p.setNaturalidadeCidade(req.naturalidadeCidade);
        p.setNaturalidadeUf(maiuscula(req.naturalidadeUf));
        p.setTelefone(req.telefone);
        p.setEndereco(req.endereco);
        p.setCidade(req.cidade);
        p.setEstado(maiuscula(req.estado));
        p.setCep(req.cep);
        p.setApelido(req.apelido);
        p.setSteamId(req.steamId);
        p.setDiscord(req.discord);
        p.setSobre(req.sobre);

        // Imagem só é trocada quando vem no corpo — mandar null não apaga a antiga.
        if (req.fotoBase64 != null) {
            p.setFotoBase64(validarImagem(req.fotoBase64, "foto"));
        }
        if (req.assinaturaBase64 != null) {
            p.setAssinaturaBase64(validarImagem(req.assinaturaBase64, "assinatura"));
        }

        return comoMapa(perfis.save(p));
    }

    private String validarImagem(String base64, String qual) {
        if (base64.isBlank()) return null;   // string vazia = remover
        if (base64.length() > LIMITE_IMAGEM) {
            throw new IllegalArgumentException("A " + qual + " ficou grande demais. Envie uma imagem menor.");
        }
        return base64;
    }

    private String maiuscula(String s) {
        return s == null ? null : s.toUpperCase();
    }

    private Perfil obterOuCriar(UUID usuarioId) {
        return perfis.findByUsuarioId(usuarioId).orElseGet(() -> {
            Perfil novo = new Perfil();
            novo.setUsuario(usuarios.findById(usuarioId).orElseThrow());
            return perfis.save(novo);
        });
    }

    private Map<String, Object> comoMapa(Perfil p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nomeCompleto", p.getNomeCompleto());
        m.put("dataNascimento", p.getDataNascimento());
        m.put("cpf", p.getCpf());
        m.put("rg", p.getRg());
        m.put("orgaoEmissor", p.getOrgaoEmissor());
        m.put("ufEmissor", p.getUfEmissor());
        m.put("nomeMae", p.getNomeMae());
        m.put("nomePai", p.getNomePai());
        m.put("naturalidadeCidade", p.getNaturalidadeCidade());
        m.put("naturalidadeUf", p.getNaturalidadeUf());
        m.put("fotoBase64", p.getFotoBase64());
        m.put("assinaturaBase64", p.getAssinaturaBase64());
        m.put("telefone", p.getTelefone());
        m.put("endereco", p.getEndereco());
        m.put("cidade", p.getCidade());
        m.put("estado", p.getEstado());
        m.put("cep", p.getCep());
        m.put("apelido", p.getApelido());
        m.put("steamId", p.getSteamId());
        m.put("discord", p.getDiscord());
        m.put("sobre", p.getSobre());
        m.put("prontoParaCnh", p.prontoParaCnh());
        return m;
    }
}
