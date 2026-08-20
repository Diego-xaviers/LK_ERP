package com.lktransportes.model;

import jakarta.persistence.*;

/** Evento genérico de roleplay — sem categorização obrigatória. */
@Entity
@DiscriminatorValue("OCORRENCIA")
public class Ocorrencia extends EventoViagem {

    /** Anulável no schema por causa do SINGLE_TABLE — ver nota em Manutencao. */
    @Column
    private String titulo;

    @Column(length = 2000)
    private String descricao;

    @Column(name = "evidencia_url")
    private String evidenciaUrl;

    @Override
    public String descricaoCurta() {
        return "Ocorrência — " + titulo;
    }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String d) { this.descricao = d; }
    public String getEvidenciaUrl() { return evidenciaUrl; }
    public void setEvidenciaUrl(String v) { this.evidenciaUrl = v; }
}
