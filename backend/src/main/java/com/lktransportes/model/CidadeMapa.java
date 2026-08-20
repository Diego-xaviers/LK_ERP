package com.lktransportes.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uma cidade que o jogo já reportou. A lista delas é a impressão digital do mapa. */
@Entity
@Table(name = "cidades_mapa")
public class CidadeMapa {

    @Id @GeneratedValue
    private UUID id;

    /** Id interno do mod (ex.: "sinop"), não o nome exibido. */
    @Column(name = "id_jogo", nullable = false, unique = true, length = 80)
    private String idJogo;

    @Column(length = 80)
    private String nome;

    @Column(name = "vista_em", nullable = false)
    private LocalDateTime vistaEm = LocalDateTime.now();

    @Column(name = "vezes_vista", nullable = false)
    private Integer vezesVista = 1;

    public void marcarVista(String nomeAtual) {
        this.vezesVista = (vezesVista == null ? 0 : vezesVista) + 1;
        if (nomeAtual != null && !nomeAtual.isBlank()) this.nome = nomeAtual;
    }

    public UUID getId() { return id; }
    public String getIdJogo() { return idJogo; }
    public void setIdJogo(String v) { this.idJogo = v; }
    public String getNome() { return nome; }
    public void setNome(String v) { this.nome = v; }
    public LocalDateTime getVistaEm() { return vistaEm; }
    public Integer getVezesVista() { return vezesVista; }
    public void setVezesVista(Integer v) { this.vezesVista = v; }
}
