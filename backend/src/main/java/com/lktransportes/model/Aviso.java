package com.lktransportes.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Comunicado publicado pelo admin — aparece no mural do painel dos motoristas. */
@Entity
@Table(name = "avisos")
public class Aviso {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAviso tipo = TipoAviso.INFORMATIVO;

    /** Aviso fixado aparece como faixa no topo do painel. */
    @Column(nullable = false)
    private boolean fixado = false;

    @Column(name = "publicado_em", nullable = false)
    private LocalDateTime publicadoEm = LocalDateTime.now();

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @ManyToOne @JoinColumn(name = "autor_id")
    private Usuario autor;

    public enum TipoAviso { INFORMATIVO, ALERTA, EVENTO }

    public boolean estaVigente() {
        return expiraEm == null || expiraEm.isAfter(LocalDateTime.now());
    }

    public UUID getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String t) { this.titulo = t; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String m) { this.mensagem = m; }
    public TipoAviso getTipo() { return tipo; }
    public void setTipo(TipoAviso tipo) { this.tipo = tipo; }
    public boolean isFixado() { return fixado; }
    public void setFixado(boolean f) { this.fixado = f; }
    public LocalDateTime getPublicadoEm() { return publicadoEm; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime e) { this.expiraEm = e; }
    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }
}
