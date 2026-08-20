package com.lktransportes.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Último estado que o agente mandou, um por motorista (sobrescrito a cada ping).
 * É o que alimenta o painel ao vivo — não guarda histórico de propósito, senão
 * viraria uma tabela de milhões de linhas por viagem.
 */
@Entity
@Table(name = "telemetria_sessao")
public class TelemetriaSessao {

    @Id @GeneratedValue
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "motorista_id", unique = true)
    private Usuario motorista;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    private Double velocidadeKmh;
    private Double rpm;
    private Integer marcha;
    private Double combustivelL;
    private Double combustivelCapacidadeL;
    private Double odometroKm;

    private Double danoMotorPct;
    private Double danoCambioPct;
    private Double danoCabinePct;
    private Double danoChassiPct;
    private Double danoRodasPct;
    private Double danoCargaPct;

    private Double posX;
    private Double posY;
    private Double posZ;

    private Boolean pilotoAutomatico;
    private Boolean pausado;
    private Boolean emServico;

    @Column(length = 80) private String cargaNome;
    private Double cargaMassaKg;
    @Column(length = 80) private String cidadeOrigem;
    @Column(length = 80) private String cidadeDestino;
    @Column(length = 80) private String empresaOrigem;
    @Column(length = 80) private String empresaDestino;
    private Integer distanciaPlanejadaKm;
    @Column(length = 80) private String placaCaminhao;
    @Column(length = 80) private String modeloCaminhao;

    /** Considera o motorista online se o último ping tem menos de 30 s. */
    public boolean estaOnline() {
        return atualizadoEm != null && atualizadoEm.isAfter(LocalDateTime.now().minusSeconds(30));
    }

    public UUID getId() { return id; }
    public Usuario getMotorista() { return motorista; }
    public void setMotorista(Usuario m) { this.motorista = m; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime v) { this.atualizadoEm = v; }
    public Double getVelocidadeKmh() { return velocidadeKmh; }
    public void setVelocidadeKmh(Double v) { this.velocidadeKmh = v; }
    public Double getRpm() { return rpm; }
    public void setRpm(Double v) { this.rpm = v; }
    public Integer getMarcha() { return marcha; }
    public void setMarcha(Integer v) { this.marcha = v; }
    public Double getCombustivelL() { return combustivelL; }
    public void setCombustivelL(Double v) { this.combustivelL = v; }
    public Double getCombustivelCapacidadeL() { return combustivelCapacidadeL; }
    public void setCombustivelCapacidadeL(Double v) { this.combustivelCapacidadeL = v; }
    public Double getOdometroKm() { return odometroKm; }
    public void setOdometroKm(Double v) { this.odometroKm = v; }
    public Double getDanoMotorPct() { return danoMotorPct; }
    public void setDanoMotorPct(Double v) { this.danoMotorPct = v; }
    public Double getDanoCambioPct() { return danoCambioPct; }
    public void setDanoCambioPct(Double v) { this.danoCambioPct = v; }
    public Double getDanoCabinePct() { return danoCabinePct; }
    public void setDanoCabinePct(Double v) { this.danoCabinePct = v; }
    public Double getDanoChassiPct() { return danoChassiPct; }
    public void setDanoChassiPct(Double v) { this.danoChassiPct = v; }
    public Double getDanoRodasPct() { return danoRodasPct; }
    public void setDanoRodasPct(Double v) { this.danoRodasPct = v; }
    public Double getDanoCargaPct() { return danoCargaPct; }
    public void setDanoCargaPct(Double v) { this.danoCargaPct = v; }
    public Double getPosX() { return posX; }
    public void setPosX(Double v) { this.posX = v; }
    public Double getPosY() { return posY; }
    public void setPosY(Double v) { this.posY = v; }
    public Double getPosZ() { return posZ; }
    public void setPosZ(Double v) { this.posZ = v; }
    public Boolean getPilotoAutomatico() { return pilotoAutomatico; }
    public void setPilotoAutomatico(Boolean v) { this.pilotoAutomatico = v; }
    public Boolean getPausado() { return pausado; }
    public void setPausado(Boolean v) { this.pausado = v; }
    public Boolean getEmServico() { return emServico; }
    public void setEmServico(Boolean v) { this.emServico = v; }
    public String getCargaNome() { return cargaNome; }
    public void setCargaNome(String v) { this.cargaNome = v; }
    public Double getCargaMassaKg() { return cargaMassaKg; }
    public void setCargaMassaKg(Double v) { this.cargaMassaKg = v; }
    public String getCidadeOrigem() { return cidadeOrigem; }
    public void setCidadeOrigem(String v) { this.cidadeOrigem = v; }
    public String getCidadeDestino() { return cidadeDestino; }
    public void setCidadeDestino(String v) { this.cidadeDestino = v; }
    public String getEmpresaOrigem() { return empresaOrigem; }
    public void setEmpresaOrigem(String v) { this.empresaOrigem = v; }
    public String getEmpresaDestino() { return empresaDestino; }
    public void setEmpresaDestino(String v) { this.empresaDestino = v; }
    public Integer getDistanciaPlanejadaKm() { return distanciaPlanejadaKm; }
    public void setDistanciaPlanejadaKm(Integer v) { this.distanciaPlanejadaKm = v; }
    public String getPlacaCaminhao() { return placaCaminhao; }
    public void setPlacaCaminhao(String v) { this.placaCaminhao = v; }
    public String getModeloCaminhao() { return modeloCaminhao; }
    public void setModeloCaminhao(String v) { this.modeloCaminhao = v; }
}
