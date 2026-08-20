package com.lktransportes.dto;

import com.lktransportes.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cadastro de motorista/gestor feito pelo admin — diferente de /auth/cadastro, entra já aprovado. */
public class NovoUsuarioRequest {
    @NotBlank
    public String nome;
    @Email @NotBlank
    public String email;
    @NotBlank
    public String senha;
    @NotNull
    public Usuario.Papel papel;
}
