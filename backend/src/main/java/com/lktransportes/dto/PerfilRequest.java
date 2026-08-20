package com.lktransportes.dto;

import java.time.LocalDate;

/** O que a tela de perfil manda de volta. Tudo opcional: o motorista preenche aos poucos. */
public class PerfilRequest {
    public String nomeCompleto;
    public LocalDate dataNascimento;
    public String cpf;
    public String rg;
    public String orgaoEmissor;
    public String ufEmissor;
    public String nomeMae;
    public String nomePai;
    public String naturalidadeCidade;
    public String naturalidadeUf;

    public String fotoBase64;
    public String assinaturaBase64;

    public String telefone;
    public String endereco;
    public String cidade;
    public String estado;
    public String cep;

    public String apelido;
    public String steamId;
    public String discord;
    public String sobre;
}
