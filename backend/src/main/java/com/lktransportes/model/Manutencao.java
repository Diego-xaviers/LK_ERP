package com.lktransportes.model;

import jakarta.persistence.*;

/**
 * Herança SINGLE_TABLE: estas colunas moram na mesma tabela de TODOS os eventos,
 * então não podem ser NOT NULL — um abastecimento ou pedágio, que não têm oficina
 * nem caminhão próprios, gravariam null e o insert quebraria. A obrigatoriedade
 * destes campos é da manutenção, e pertence à validação, não ao schema.
 */
@Entity
@DiscriminatorValue("MANUTENCAO")
public class Manutencao extends EventoViagem {

    @ManyToOne
    @JoinColumn(name = "oficina_id")
    private Oficina oficina;

    /** Serviço realizado — texto livre, sem ordem de serviço nem estoque. */
    @Column(length = 1000)
    private String servico;

    /**
     * Guardado também aqui pra que a manutenção apareça no histórico do
     * caminhão mesmo se a viagem for consultada por outro caminho.
     */
    @ManyToOne
    @JoinColumn(name = "caminhao_id")
    private Caminhao caminhao;

    @Override
    public String descricaoCurta() {
        return "Manutenção — " + servico;
    }

    public Oficina getOficina() { return oficina; }
    public void setOficina(Oficina oficina) { this.oficina = oficina; }
    public String getServico() { return servico; }
    public void setServico(String servico) { this.servico = servico; }
    public Caminhao getCaminhao() { return caminhao; }
    public void setCaminhao(Caminhao c) { this.caminhao = c; }
}
