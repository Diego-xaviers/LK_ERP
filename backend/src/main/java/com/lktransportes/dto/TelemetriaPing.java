package com.lktransportes.dto;

/** Espelho exato do JSON que o agente PowerShell envia. */
public class TelemetriaPing {
    public Boolean jogoAtivo;
    public Boolean pausado;
    public Integer tempoJogoMin;

    public Double velocidadeKmh;
    public Double rpm;
    public Integer marcha;

    public Double combustivelL;
    public Double combustivelCapacidadeL;
    public Double consumoMedioLKm;

    public Double odometroKm;

    public Double desgasteMotorPct;
    public Double desgasteCambioPct;
    public Double desgasteCabinePct;
    public Double desgasteChassiPct;
    public Double desgasteRodasPct;
    public Double desgasteCargaPct;

    public Double posX;
    public Double posY;
    public Double posZ;

    public Boolean pilotoAutomatico;
    public Double pilotoAutomaticoKmh;
    public Boolean estacionamentoAutomatico;

    public Boolean emServico;
    public Boolean abastecendo;
    public Boolean entregaFeita;

    public String cargaNome;
    public Double cargaMassaKg;
    public String cidadeOrigem;
    /** Id interno do mod — é ele que identifica o mapa, não o nome. */
    public String cidadeOrigemId;
    public String cidadeDestino;
    public String cidadeDestinoId;
    public String empresaOrigem;
    public String empresaDestino;
    public Integer distanciaPlanejadaKm;

    public String placaCaminhao;
    public String modeloCaminhao;
    public Integer jogo;

    /** O dano do caminhão é o pior entre os componentes. */
    public double danoCaminhaoPct() {
        return Math.max(Math.max(nz(desgasteMotorPct), nz(desgasteCambioPct)),
               Math.max(Math.max(nz(desgasteCabinePct), nz(desgasteChassiPct)), nz(desgasteRodasPct)));
    }

    private static double nz(Double v) { return v == null ? 0 : v; }
}
