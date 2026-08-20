package com.lktransportes.model;

import jakarta.persistence.*;

/**
 * Multa é apenas um EVENTO ocorrido no jogo.
 * Sem catálogo de infrações e sem validação: as multas do mapa RBR são
 * dinâmicas, então o motivo é texto livre e o valor é o que o jogo cobrou.
 */
@Entity
@DiscriminatorValue("MULTA")
public class Multa extends EventoViagem {

    /** Anulável no schema por causa do SINGLE_TABLE — ver nota em Manutencao. */
    @Column(length = 500)
    private String motivo;

    @Column(name = "local_multa")
    private String local;

    /** Caminho/URL do print de evidência, se o motorista anexar. */
    @Column(name = "evidencia_url")
    private String evidenciaUrl;

    @Override
    public String descricaoCurta() {
        return "Multa — " + motivo;
    }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }
    public String getEvidenciaUrl() { return evidenciaUrl; }
    public void setEvidenciaUrl(String v) { this.evidenciaUrl = v; }
}
