package com.lktransportes.model;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * O que o painel sabe sobre o mapa em que a transportadora opera.
 *
 * Não dá para embutir uma lista de cidades do RBR no código: o mod muda, cada
 * servidor roda uma versão. Então o sistema APRENDE com as primeiras viagens
 * reais e depois o gestor tranca. A partir daí, viagem em cidade desconhecida
 * é viagem em outro mapa.
 */
@Entity
@Table(name = "mapa_conhecido")
public class MapaConhecido {

    @Id @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Modo modo = Modo.APRENDENDO;

    /** Caixa que envolve tudo que já foi visto, em coordenadas do jogo. */
    private Double minX;
    private Double maxX;
    private Double minZ;
    private Double maxZ;

    /** Folga aplicada à caixa, pra borda do mapa não gerar falso positivo. */
    @Column(nullable = false)
    private Double margemMetros = 20000d;

    public enum Modo {
        /** Coletando cidades e coordenadas. Não bloqueia ninguém. */
        APRENDENDO,
        /** Trancado: viagem fora do que foi aprendido é retida. */
        ATIVO
    }

    /** Cresce a caixa para caber a posição vista. */
    public void registrarPosicao(double x, double z) {
        if (minX == null || x < minX) minX = x;
        if (maxX == null || x > maxX) maxX = x;
        if (minZ == null || z < minZ) minZ = z;
        if (maxZ == null || z > maxZ) maxZ = z;
    }

    /** Sem caixa aprendida ainda, nada está fora — não dá para acusar sem referência. */
    public boolean dentroDaArea(double x, double z) {
        if (minX == null || maxX == null || minZ == null || maxZ == null) return true;
        double m = margemMetros == null ? 0 : margemMetros;
        return x >= minX - m && x <= maxX + m && z >= minZ - m && z <= maxZ + m;
    }

    public boolean temAreaDefinida() {
        return minX != null && maxX != null && minZ != null && maxZ != null;
    }

    public UUID getId() { return id; }
    public Modo getModo() { return modo; }
    public void setModo(Modo m) { this.modo = m; }
    public Double getMinX() { return minX; }
    public void setMinX(Double v) { this.minX = v; }
    public Double getMaxX() { return maxX; }
    public void setMaxX(Double v) { this.maxX = v; }
    public Double getMinZ() { return minZ; }
    public void setMinZ(Double v) { this.minZ = v; }
    public Double getMaxZ() { return maxZ; }
    public void setMaxZ(Double v) { this.maxZ = v; }
    public Double getMargemMetros() { return margemMetros; }
    public void setMargemMetros(Double v) { this.margemMetros = v; }
}
