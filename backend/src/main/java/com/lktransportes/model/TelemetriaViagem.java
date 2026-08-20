package com.lktransportes.model;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * O que a telemetria apurou de uma viagem específica. Fica separado de Viagem
 * porque é dado observado (o jogo reportou), não declarado (o motorista digitou)
 * — misturar os dois é o que estragaria a conferência do gestor.
 */
@Entity
@Table(name = "telemetria_viagem")
public class TelemetriaViagem {

    @Id @GeneratedValue
    private UUID id;

    /**
     * Fora do JSON: quem consulta já sabe de que viagem se trata, e serializar a
     * Viagem aqui puxaria a coleção lazy de eventos fora da transação.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "viagem_id", unique = true)
    private Viagem viagem;

    // ----- Distância e consumo -----
    private Double odometroInicialKm;
    private Double odometroAtualKm;
    private Double combustivelInicialL;
    private Double combustivelAtualL;
    /** Soma de tudo que entrou no tanque durante a viagem. */
    private Double litrosAbastecidos = 0d;

    // ----- Dano -----
    private Double danoInicialPct;
    private Double danoAtualPct;
    /** Até quanto de dano já virou ocorrência, pra não gerar evento repetido. */
    private Double danoRegistradoPct;

    // ----- Detecção de abastecimento em curso -----
    private Boolean abastecendo = false;
    private Double combustivelAoIniciarAbastecimento;

    // ----- O que o jogo disse que estava sendo levado -----
    /** Guardado por viagem (não só na sessão) pra sobreviver ao fim da viagem. */
    @Column(name = "carga_jogo", length = 80)
    private String cargaJogo;

    @Column(name = "peso_jogo_kg")
    private Double pesoJogoKg;

    /** Maior distância que o jogo confirmou ter sido percorrida. */
    /** Ids das cidades do jogo — a prova de qual mapa estava rodando. */
    @Column(name = "cidade_origem_id", length = 80)
    private String cidadeOrigemId;

    @Column(name = "cidade_destino_id", length = 80)
    private String cidadeDestinoId;

    /** Alguma posição caiu fora da área conhecida do mapa. */
    @Column(name = "fora_da_area")
    private Boolean foraDaArea = false;

    @Column(name = "distancia_confirmada_km")
    private Double distanciaConfirmadaKm;

    // ----- Anti-fraude -----
    private Boolean usouPilotoAutomatico = false;
    private Boolean usouEstacionamentoAutomatico = false;
    /** Saltos de posição grandes demais para serem dirigidos (teleporte/reboque). */
    private Integer saltos = 0;
    @Column(length = 500)
    private String divergencias;

    /** Distância que o jogo confirma ter sido rodada nesta viagem. */
    public Double distanciaPercorridaKm() {
        if (odometroInicialKm == null || odometroAtualKm == null) return null;
        double d = odometroAtualKm - odometroInicialKm;
        return d < 0 ? null : Math.round(d * 10) / 10d;   // odômetro não anda pra trás
    }

    /** Gasto real = o que sumiu do tanque + o que foi reabastecido no meio. */
    public Double combustivelGastoL() {
        if (combustivelInicialL == null || combustivelAtualL == null) return null;
        double g = combustivelInicialL - combustivelAtualL + (litrosAbastecidos == null ? 0 : litrosAbastecidos);
        return g < 0 ? null : Math.round(g * 10) / 10d;
    }

    public UUID getId() { return id; }
    public Viagem getViagem() { return viagem; }
    public void setViagem(Viagem v) { this.viagem = v; }
    public Double getOdometroInicialKm() { return odometroInicialKm; }
    public void setOdometroInicialKm(Double v) { this.odometroInicialKm = v; }
    public Double getOdometroAtualKm() { return odometroAtualKm; }
    public void setOdometroAtualKm(Double v) { this.odometroAtualKm = v; }
    public Double getCombustivelInicialL() { return combustivelInicialL; }
    public void setCombustivelInicialL(Double v) { this.combustivelInicialL = v; }
    public Double getCombustivelAtualL() { return combustivelAtualL; }
    public void setCombustivelAtualL(Double v) { this.combustivelAtualL = v; }
    public Double getLitrosAbastecidos() { return litrosAbastecidos; }
    public void setLitrosAbastecidos(Double v) { this.litrosAbastecidos = v; }
    public Double getDanoInicialPct() { return danoInicialPct; }
    public void setDanoInicialPct(Double v) { this.danoInicialPct = v; }
    public Double getDanoAtualPct() { return danoAtualPct; }
    public void setDanoAtualPct(Double v) { this.danoAtualPct = v; }
    public Double getDanoRegistradoPct() { return danoRegistradoPct; }
    public void setDanoRegistradoPct(Double v) { this.danoRegistradoPct = v; }
    public Boolean getAbastecendo() { return abastecendo != null && abastecendo; }
    public void setAbastecendo(Boolean v) { this.abastecendo = v; }
    public Double getCombustivelAoIniciarAbastecimento() { return combustivelAoIniciarAbastecimento; }
    public void setCombustivelAoIniciarAbastecimento(Double v) { this.combustivelAoIniciarAbastecimento = v; }
    public Boolean getUsouPilotoAutomatico() { return usouPilotoAutomatico != null && usouPilotoAutomatico; }
    public void setUsouPilotoAutomatico(Boolean v) { this.usouPilotoAutomatico = v; }
    public Boolean getUsouEstacionamentoAutomatico() { return usouEstacionamentoAutomatico != null && usouEstacionamentoAutomatico; }
    public void setUsouEstacionamentoAutomatico(Boolean v) { this.usouEstacionamentoAutomatico = v; }
    public Integer getSaltos() { return saltos == null ? 0 : saltos; }
    public void setSaltos(Integer v) { this.saltos = v; }
    public String getDivergencias() { return divergencias; }
    public void setDivergencias(String v) { this.divergencias = v; }
    public String getCargaJogo() { return cargaJogo; }
    public void setCargaJogo(String v) { this.cargaJogo = v; }
    public Double getPesoJogoKg() { return pesoJogoKg; }
    public void setPesoJogoKg(Double v) { this.pesoJogoKg = v; }
    public Double getDistanciaConfirmadaKm() { return distanciaConfirmadaKm; }
    public void setDistanciaConfirmadaKm(Double v) { this.distanciaConfirmadaKm = v; }
    public String getCidadeOrigemId() { return cidadeOrigemId; }
    public void setCidadeOrigemId(String v) { this.cidadeOrigemId = v; }
    public String getCidadeDestinoId() { return cidadeDestinoId; }
    public void setCidadeDestinoId(String v) { this.cidadeDestinoId = v; }
    public Boolean getForaDaArea() { return foraDaArea != null && foraDaArea; }
    public void setForaDaArea(Boolean v) { this.foraDaArea = v; }
}
