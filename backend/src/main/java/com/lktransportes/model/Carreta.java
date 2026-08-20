package com.lktransportes.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "carretas")
public class Carreta {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String tipo;          // ex.: Graneleira, Baú, Bitrem

    @Column(nullable = false, unique = true)
    private String placa;

    @Column(name = "identificacao_interna")
    private String identificacaoInterna;

    @Column(nullable = false)
    private boolean ativa = true;

    public UUID getId() { return id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public String getIdentificacaoInterna() { return identificacaoInterna; }
    public void setIdentificacaoInterna(String v) { this.identificacaoInterna = v; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
}
