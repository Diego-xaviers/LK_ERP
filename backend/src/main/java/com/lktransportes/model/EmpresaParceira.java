package com.lktransportes.model;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Empresa parceira do universo da transportadora no mapa RBR — usada como
 * remetente/destinatária ao criar uma viagem, evitando digitar tudo de novo.
 */
@Entity
@Table(name = "empresas_parceiras")
public class EmpresaParceira {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String nome;

    /** Ramo/segmento, ex.: Agro, Combustíveis, Frigorífico. */
    private String segmento;

    @Column(nullable = false)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String estado;

    /** CNPJ fictício, apenas para compor os documentos de simulação. */
    @Column(name = "cnpj_ficticio", length = 20)
    private String cnpjFicticio;

    @Column(nullable = false)
    private boolean ativa = true;

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getSegmento() { return segmento; }
    public void setSegmento(String s) { this.segmento = s; }
    public String getCidade() { return cidade; }
    public void setCidade(String c) { this.cidade = c; }
    public String getEstado() { return estado; }
    public void setEstado(String e) { this.estado = e; }
    public String getCnpjFicticio() { return cnpjFicticio; }
    public void setCnpjFicticio(String c) { this.cnpjFicticio = c; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
}
