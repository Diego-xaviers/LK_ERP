package com.lktransportes.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel = Papel.MOTORISTA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAcesso statusAcesso = StatusAcesso.PENDENTE;

    /** Créditos que o motorista tem para gastar na loja. */
    @Column(name = "saldo_carteira", nullable = false, precision = 14, scale = 2)
    private java.math.BigDecimal saldoCarteira = java.math.BigDecimal.ZERO;

    /** Comissão própria deste motorista. Nulo = usa o padrão da empresa. */
    /** Valor por km próprio deste motorista. Nulo = usa o padrão da empresa. */
    @Column(name = "valor_km_comissao", precision = 8, scale = 3)
    private java.math.BigDecimal valorKmComissao;

    /** Segredo que o agente de telemetria usa para se identificar. Nulo até o motorista gerar. */
    @Column(name = "token_telemetria", unique = true, length = 64)
    private String tokenTelemetria;

    public enum Papel { MOTORISTA, GESTOR }
    public enum StatusAcesso { PENDENTE, APROVADO, BLOQUEADO }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public java.math.BigDecimal getSaldoCarteira() { return saldoCarteira; }
    public void setSaldoCarteira(java.math.BigDecimal v) { this.saldoCarteira = v; }
    public java.math.BigDecimal getValorKmComissao() { return valorKmComissao; }
    public void setValorKmComissao(java.math.BigDecimal v) { this.valorKmComissao = v; }

    @JsonIgnore
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public Papel getPapel() { return papel; }
    public void setPapel(Papel papel) { this.papel = papel; }
    public StatusAcesso getStatusAcesso() { return statusAcesso; }
    public void setStatusAcesso(StatusAcesso statusAcesso) { this.statusAcesso = statusAcesso; }

    /** Não vai pro JSON: quem precisa dele recebe pelo endpoint próprio da telemetria. */
    @JsonIgnore
    public String getTokenTelemetria() { return tokenTelemetria; }
    public void setTokenTelemetria(String t) { this.tokenTelemetria = t; }
}
