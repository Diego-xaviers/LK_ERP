package com.lktransportes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CadastroRequest {
    @NotBlank
    public String nome;
    @Email @NotBlank
    public String email;
    @NotBlank
    public String senha;
}
