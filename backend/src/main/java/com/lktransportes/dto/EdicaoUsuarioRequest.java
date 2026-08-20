package com.lktransportes.dto;

import com.lktransportes.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EdicaoUsuarioRequest {
    @NotBlank
    public String nome;
    @Email @NotBlank
    public String email;
    @NotNull
    public Usuario.Papel papel;
    /** Opcional — só troca a senha se vier preenchida. */
    public String novaSenha;
}
