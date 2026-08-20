package com.lktransportes.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Habilitação de roleplay do motorista. Tem prazo e tem pontos: vencer ou
 * zerar os pontos tira o motorista de circulação até a gestão reabilitar.
 *
 * É o que dá consequência ao comportamento — sem isso, multa e avaria seriam
 * só um número no histórico.
 */
@Entity
@Table(name = "cnhs")
public class Cnh {

    /** Todo motorista começa com esta pontuação e vai perdendo. */
    public static final int PONTOS_INICIAIS = 20;

    @Id @GeneratedValue
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "motorista_id", unique = true)
    private Usuario motorista;

    @Column(name = "numero_registro", nullable = false, length = 20)
    private String numeroRegistro;

    @Column(nullable = false, length = 10)
    private String categoria = "E";

    @Column(name = "primeira_habilitacao")
    private LocalDate primeiraHabilitacao;

    @Column(nullable = false)
    private LocalDate validade;

    @Column(nullable = false)
    private Integer pontos = PONTOS_INICIAIS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Situacao situacao = Situacao.ATIVA;

    @Column(name = "emitida_em", nullable = false)
    private LocalDateTime emitidaEm = LocalDateTime.now();

    @ManyToOne @JoinColumn(name = "emitida_por_id")
    private Usuario emitidaPor;

    @Column(length = 400)
    private String observacoes;

    /** SUSPENSA é decisão registrada; VENCIDA é consequência do calendário. */
    public enum Situacao { ATIVA, SUSPENSA }

    // ----- Regras -----

    public boolean vencida() {
        return LocalDate.now().isAfter(validade);
    }

    public boolean valida() {
        return situacao == Situacao.ATIVA && !vencida() && pontos > 0;
    }

    /** Rótulo único para a tela, juntando prazo, pontos e decisão do gestor. */
    public String estado() {
        if (situacao == Situacao.SUSPENSA) return "SUSPENSA";
        if (vencida()) return "VENCIDA";
        if (pontos <= 0) return "SUSPENSA";
        return "ATIVA";
    }

    public String motivoDoBloqueio() {
        // Pontuação primeiro: quando ela zera, a própria carteira se suspende, e
        // dizer "suspensa pela gestão" nesse caso seria mentira.
        if (pontos <= 0) return "CNH suspensa por pontuação zerada.";
        if (vencida()) return "CNH vencida em " + validade + ".";
        if (situacao == Situacao.SUSPENSA) return "CNH suspensa pela gestão.";
        return null;
    }

    /**
     * Desconta pontos e suspende sozinha ao chegar em zero.
     * @return quantos pontos realmente saíram (não passa de zero).
     */
    public int descontar(int quantos) {
        int antes = pontos;
        pontos = Math.max(0, pontos - quantos);
        if (pontos == 0) {
            situacao = Situacao.SUSPENSA;
        }
        return antes - pontos;
    }

    /** Renovação devolve prazo e pontos cheios. */
    public void renovar(LocalDate novaValidade) {
        this.validade = novaValidade;
        this.pontos = PONTOS_INICIAIS;
        this.situacao = Situacao.ATIVA;
    }

    public UUID getId() { return id; }
    public Usuario getMotorista() { return motorista; }
    public void setMotorista(Usuario m) { this.motorista = m; }
    public String getNumeroRegistro() { return numeroRegistro; }
    public void setNumeroRegistro(String v) { this.numeroRegistro = v; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String v) { this.categoria = v; }
    public LocalDate getPrimeiraHabilitacao() { return primeiraHabilitacao; }
    public void setPrimeiraHabilitacao(LocalDate v) { this.primeiraHabilitacao = v; }
    public LocalDate getValidade() { return validade; }
    public void setValidade(LocalDate v) { this.validade = v; }
    public Integer getPontos() { return pontos; }
    public void setPontos(Integer v) { this.pontos = v; }
    public Situacao getSituacao() { return situacao; }
    public void setSituacao(Situacao v) { this.situacao = v; }
    public LocalDateTime getEmitidaEm() { return emitidaEm; }
    public void setEmitidaEm(LocalDateTime v) { this.emitidaEm = v; }
    public Usuario getEmitidaPor() { return emitidaPor; }
    public void setEmitidaPor(Usuario u) { this.emitidaPor = u; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String v) { this.observacoes = v; }
}
