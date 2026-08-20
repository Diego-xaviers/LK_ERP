package com.lktransportes.model;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("PEDAGIO")
public class Pedagio extends EventoViagem {

    /** Nome ou local do pedágio — texto livre, sem cadastro prévio. */
    @Column(name = "local_pedagio")
    private String local;

    @Override
    public String descricaoCurta() {
        return "Pedágio" + (local != null ? " — " + local : "");
    }

    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }
}
