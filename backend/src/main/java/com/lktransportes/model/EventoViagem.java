package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base de todo acontecimento registrado durante uma viagem.
 *
 * Herança SINGLE_TABLE: todos os eventos vivem na mesma tabela, o que deixa a
 * timeline cronológica da viagem ser uma consulta só, ordenada por ocorridoEm.
 */
@Entity
@Table(name = "eventos_viagem")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", length = 20)
public abstract class EventoViagem {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "viagem_id")
    private Viagem viagem;

    /** Quando aconteceu no jogo — preenchido automaticamente, mas corrigível. */
    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm = LocalDateTime.now();

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private LocalDateTime registradoEm = LocalDateTime.now();

    @Column(length = 1000)
    private String observacao;

    /** Custo do evento. Nulo em eventos que não têm valor (ex.: ocorrência). */
    @Column(precision = 14, scale = 2)
    private BigDecimal valor;

    /**
     * Quem criou o evento. Coluna nova e anulável de propósito: os eventos que já
     * existiam no banco vêm com null e são tratados como MANUAL na leitura.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private Origem origem = Origem.MANUAL;

    public enum Origem { MANUAL, TELEMETRIA }

    /** Rótulo curto usado na timeline. */
    public abstract String descricaoCurta();

    public UUID getId() { return id; }
    public Viagem getViagem() { return viagem; }
    public void setViagem(Viagem viagem) { this.viagem = viagem; }
    public LocalDateTime getOcorridoEm() { return ocorridoEm; }
    public void setOcorridoEm(LocalDateTime v) { this.ocorridoEm = v; }
    public LocalDateTime getRegistradoEm() { return registradoEm; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String o) { this.observacao = o; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public Origem getOrigem() { return origem == null ? Origem.MANUAL : origem; }
    public void setOrigem(Origem origem) { this.origem = origem; }
}
