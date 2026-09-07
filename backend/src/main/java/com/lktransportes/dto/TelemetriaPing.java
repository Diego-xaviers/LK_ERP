package com.lktransportes.dto;

/** Espelho exato do JSON que o agente PowerShell envia. */
public class TelemetriaPing {
    /** Versão 2 envia eventos duráveis, com confirmação individual. */
    public Integer protocolo;
    public java.util.UUID agenteJobId;
    public java.util.List<MultaAgente> multas;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.List<java.util.UUID> multasConfirmadas = java.util.List.of();
    public record MultaAgente(java.util.UUID id, java.util.UUID agenteJobId,
                              java.math.BigDecimal valor, java.time.Instant ocorridoEm) {}
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

    /** Acumuladores financeiros do job atual (int64 do scs-sdk-plugin, offset 4216/4224/4232). */
    public Long fineAccumulator;
    public Long tollAccumulator;
    public Long ferryAccumulator;

    /** O plugin alterna estes sinais em CADA evento; não são pulsos true/false. */
    public Boolean fined;
    public Boolean tollgate;

    /** O dano do caminhão é o pior entre os componentes. */
    public double danoCaminhaoPct() {
        return Math.max(Math.max(nz(desgasteMotorPct), nz(desgasteCambioPct)),
               Math.max(Math.max(nz(desgasteCabinePct), nz(desgasteChassiPct)), nz(desgasteRodasPct)));
    }

    private static double nz(Double v) { return v == null ? 0 : v; }
}
