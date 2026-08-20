package com.lktransportes.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "caminhoes")
public class Caminhao {

    /**
     * Dono do caminhão. Nulo = da empresa, disponível a todos. Com dono, só ele
     * (ou um gestor) dirige — é assim que o motorista fica preso ao que é dele.
     */
    @ManyToOne @JoinColumn(name = "dono_id")
    private Usuario dono;

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String placa;

    private String modelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCaminhao status = StatusCaminhao.ATIVO;

    @Column(nullable = false)
    private String marca;

    @Column(name = "identificacao_interna")
    private String identificacaoInterna;

    public enum StatusCaminhao { ATIVO, MANUTENCAO }

    public UUID getId() { return id; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public StatusCaminhao getStatus() { return status; }
    public void setStatus(StatusCaminhao status) { this.status = status; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getIdentificacaoInterna() { return identificacaoInterna; }
    public void setIdentificacaoInterna(String v) { this.identificacaoInterna = v; }
    public Usuario getDono() { return dono; }
    public void setDono(Usuario u) { this.dono = u; }

    /** Da empresa, ou deste motorista. */
    public boolean podeSerUsadoPor(java.util.UUID motoristaId) {
        return dono == null || dono.getId().equals(motoristaId);
    }
}
