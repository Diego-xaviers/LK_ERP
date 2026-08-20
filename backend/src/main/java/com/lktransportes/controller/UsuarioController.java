package com.lktransportes.controller;

import com.lktransportes.dto.EdicaoUsuarioRequest;
import com.lktransportes.dto.NovoUsuarioRequest;
import com.lktransportes.model.Usuario;
import com.lktransportes.repository.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository repository;
    private final com.lktransportes.security.SessaoAtual sessao;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UsuarioController(UsuarioRepository repository, com.lktransportes.security.SessaoAtual sessao) {
        this.repository = repository;
        this.sessao = sessao;
    }

    @GetMapping
    public List<Usuario> listar() {
        return repository.findAll();
    }

    // TODO: restringir a GESTOR via Spring Security (@PreAuthorize)
    @PostMapping
    public Usuario criar(@Valid @RequestBody NovoUsuarioRequest req) {
        if (repository.findByEmail(req.email).isPresent()) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }
        Usuario u = new Usuario();
        u.setNome(req.nome);
        u.setEmail(req.email);
        u.setSenhaHash(encoder.encode(req.senha));
        u.setPapel(req.papel);
        u.setStatusAcesso(Usuario.StatusAcesso.APROVADO); // cadastrado pelo admin, já entra liberado
        return repository.save(u);
    }

    @PutMapping("/{id}")
    public Usuario editar(@PathVariable UUID id, @Valid @RequestBody EdicaoUsuarioRequest req) {
        Usuario u = repository.findById(id).orElseThrow();

        repository.findByEmail(req.email).ifPresent(outro -> {
            if (!outro.getId().equals(id)) {
                throw new IllegalArgumentException("E-mail já cadastrado para outro usuário");
            }
        });

        u.setNome(req.nome);
        u.setEmail(req.email);
        u.setPapel(req.papel);
        if (req.novaSenha != null && !req.novaSenha.isBlank()) {
            u.setSenhaHash(encoder.encode(req.novaSenha));
        }
        return repository.save(u);
    }

    @DeleteMapping("/{id}")
    public void remover(@PathVariable UUID id) {
        repository.deleteById(id);
    }

    /** O usuário do token. Antes vinha de um parâmetro de URL, que era falsificável. */
    /**
     * Quem está logado agora.
     *
     * Token assinado cujo usuário não existe mais (removido pela gestão, ou banco
     * recriado em desenvolvimento) é sessão inválida, não registro faltando: por
     * isso 401 e não 404. É o 401 que faz o painel limpar o token e voltar pro
     * login — com 404 o motorista ficava preso numa tela de erro.
     */
    @GetMapping("/atual")
    public Usuario atual() {
        return repository.findById(sessao.id()).orElseThrow(
                () -> new com.lktransportes.security.SessaoAtual.SessaoInvalidaException(
                        "Sessão inválida. Entre de novo."));
    }

    @PostMapping("/{id}/aprovar")
    public Usuario aprovar(@PathVariable UUID id) {
        Usuario u = repository.findById(id).orElseThrow();
        u.setStatusAcesso(Usuario.StatusAcesso.APROVADO);
        return repository.save(u);
    }

    @PostMapping("/{id}/bloquear")
    public Usuario bloquear(@PathVariable UUID id) {
        Usuario u = repository.findById(id).orElseThrow();
        u.setStatusAcesso(Usuario.StatusAcesso.BLOQUEADO);
        return repository.save(u);
    }
}
