package com.lktransportes.controller;

import com.lktransportes.dto.CadastroRequest;
import com.lktransportes.dto.LoginRequest;
import com.lktransportes.model.Usuario;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UsuarioRepository usuarios, JwtService jwtService) {
        this.usuarios = usuarios;
        this.jwtService = jwtService;
    }

    @PostMapping("/cadastro")
    public Map<String, String> cadastrar(@Valid @RequestBody CadastroRequest req) {
        if (usuarios.findByEmail(req.email).isPresent()) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }
        Usuario u = new Usuario();
        u.setNome(req.nome);
        u.setEmail(req.email);
        u.setSenhaHash(encoder.encode(req.senha));
        u.setStatusAcesso(Usuario.StatusAcesso.PENDENTE); // aguarda aprovação do admin
        usuarios.save(u);
        return Map.of("mensagem", "Cadastro recebido. Aguardando aprovação do administrador.");
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest req) {
        Usuario u = usuarios.findByEmail(req.email)
                .orElseThrow(() -> new IllegalArgumentException("Credenciais inválidas"));

        if (!encoder.matches(req.senha, u.getSenhaHash())) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }
        if (u.getStatusAcesso() != Usuario.StatusAcesso.APROVADO) {
            throw new IllegalStateException("Cadastro ainda não aprovado pelo administrador");
        }
        String token = jwtService.gerarToken(u.getId(), u.getEmail(), u.getPapel().name());
        return Map.of("token", token, "nome", u.getNome(), "papel", u.getPapel().name());
    }
}
