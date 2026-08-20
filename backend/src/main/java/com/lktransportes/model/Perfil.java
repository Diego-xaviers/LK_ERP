package com.lktransportes.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ficha completa do motorista. Fica fora de Usuario de propósito: Usuario é
 * serializado em vários lugares (mural, listagens) e nada disso aqui pode
 * vazar junto. É também a fonte dos dados que vão desenhados na CNH.
 */
@Entity
@Table(name = "perfis")
public class Perfil {

    @Id @GeneratedValue
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    // ----- Identificação (vai para a CNH) -----
    @Column(length = 120) private String nomeCompleto;
    private LocalDate dataNascimento;
    @Column(length = 20)  private String cpf;
    @Column(length = 20)  private String rg;
    @Column(length = 30)  private String orgaoEmissor;
    @Column(length = 2)   private String ufEmissor;
    @Column(length = 120) private String nomeMae;
    @Column(length = 120) private String nomePai;
    @Column(length = 80)  private String naturalidadeCidade;
    @Column(length = 2)   private String naturalidadeUf;

    /** Foto 3x4 do motorista, PNG/JPEG em base64 — vai no retrato da CNH. */
    @Lob
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONGVARCHAR)
    @Column(name = "foto_base64", columnDefinition = "text")
    private String fotoBase64;

    /** Assinatura desenhada, reaproveitando o mesmo canvas do abastecimento. */
    @Lob
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONGVARCHAR)
    @Column(name = "assinatura_base64", columnDefinition = "text")
    private String assinaturaBase64;

    // ----- Contato e residência -----
    @Column(length = 30) private String telefone;
    @Column(length = 120) private String endereco;
    @Column(length = 80) private String cidade;
    @Column(length = 2)  private String estado;
    @Column(length = 12) private String cep;

    // ----- Vida na transportadora -----
    @Column(length = 60) private String apelido;
    @Column(length = 80) private String steamId;
    @Column(length = 60) private String discord;
    @Column(length = 600) private String sobre;

    /** A CNH precisa de nome civil e nascimento; sem isso não dá para emitir. */
    public boolean prontoParaCnh() {
        return nomeCompleto != null && !nomeCompleto.isBlank() && dataNascimento != null;
    }

    public UUID getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario u) { this.usuario = u; }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String v) { this.nomeCompleto = v; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate v) { this.dataNascimento = v; }
    public String getCpf() { return cpf; }
    public void setCpf(String v) { this.cpf = v; }
    public String getRg() { return rg; }
    public void setRg(String v) { this.rg = v; }
    public String getOrgaoEmissor() { return orgaoEmissor; }
    public void setOrgaoEmissor(String v) { this.orgaoEmissor = v; }
    public String getUfEmissor() { return ufEmissor; }
    public void setUfEmissor(String v) { this.ufEmissor = v; }
    public String getNomeMae() { return nomeMae; }
    public void setNomeMae(String v) { this.nomeMae = v; }
    public String getNomePai() { return nomePai; }
    public void setNomePai(String v) { this.nomePai = v; }
    public String getNaturalidadeCidade() { return naturalidadeCidade; }
    public void setNaturalidadeCidade(String v) { this.naturalidadeCidade = v; }
    public String getNaturalidadeUf() { return naturalidadeUf; }
    public void setNaturalidadeUf(String v) { this.naturalidadeUf = v; }
    public String getFotoBase64() { return fotoBase64; }
    public void setFotoBase64(String v) { this.fotoBase64 = v; }
    public String getAssinaturaBase64() { return assinaturaBase64; }
    public void setAssinaturaBase64(String v) { this.assinaturaBase64 = v; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String v) { this.telefone = v; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String v) { this.endereco = v; }
    public String getCidade() { return cidade; }
    public void setCidade(String v) { this.cidade = v; }
    public String getEstado() { return estado; }
    public void setEstado(String v) { this.estado = v; }
    public String getCep() { return cep; }
    public void setCep(String v) { this.cep = v; }
    public String getApelido() { return apelido; }
    public void setApelido(String v) { this.apelido = v; }
    public String getSteamId() { return steamId; }
    public void setSteamId(String v) { this.steamId = v; }
    public String getDiscord() { return discord; }
    public void setDiscord(String v) { this.discord = v; }
    public String getSobre() { return sobre; }
    public void setSobre(String v) { this.sobre = v; }
}
