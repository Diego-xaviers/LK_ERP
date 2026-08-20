package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Carga que a transportadora fechou com um cliente e distribui entre os
 * motoristas. O gestor publica; os motoristas puxam viagens dela até a
 * quantidade fechar.
 *
 * É aqui que mora o valor do frete. O motorista nunca digita quanto vale a
 * viagem dele — o número sai da tarifa desta demanda —, e é isso que tira a
 * fraude de valor da mesa antes de precisar conferir nada.
 */
@Entity
@Table(name = "demandas")
public class Demanda {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private Integer numero;

    // ----- Rota e carga -----
    @Column(nullable = false) private String origem;
    @Column(nullable = false) private String destino;
    @Column(name = "empresa_remetente", nullable = false) private String empresaRemetente;
    @Column(name = "empresa_destinataria", nullable = false) private String empresaDestinataria;
    @Column(nullable = false) private String carga;

    // ----- Regras que o gestor define (o sistema não escolhe nada disso) -----

    /** Data limite da demanda inteira. Vencida, ela fica atrasada mas segue aceitando viagens. */
    @Column(name = "prazo_entrega")
    private java.time.LocalDate prazoEntrega;

    /** Vazio = qualquer caminhão da frota serve. */
    @ManyToMany
    @JoinTable(name = "demanda_caminhoes",
            joinColumns = @JoinColumn(name = "demanda_id"),
            inverseJoinColumns = @JoinColumn(name = "caminhao_id"))
    private java.util.Set<Caminhao> caminhoesPermitidos = new java.util.HashSet<>();

    /** Vazio = qualquer reboque serve. Preenchido, exige carreta de um destes tipos. */
    @ElementCollection
    @CollectionTable(name = "demanda_reboques", joinColumns = @JoinColumn(name = "demanda_id"))
    @Column(name = "tipo", length = 40)
    private java.util.Set<String> tiposReboquePermitidos = new java.util.HashSet<>();

    /** Quanto o cliente contratou no total. */
    @Column(name = "quantidade_total_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidadeTotalKg;

    /** Soma do peso das viagens já concluídas nesta demanda. */
    @Column(name = "quantidade_entregue_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidadeEntregueKg = BigDecimal.ZERO;

    // ----- Tarifa -----
    /** Por tonelada, para a viagem parcial valer proporcionalmente. */
    @Column(name = "frete_por_tonelada", nullable = false, precision = 12, scale = 2)
    private BigDecimal fretePorTonelada;

    @Column(name = "valor_carga_por_tonelada", precision = 12, scale = 2)
    private BigDecimal valorCargaPorTonelada;

    // ----- Ciclo de vida -----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ABERTA;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @Column(length = 600)
    private String observacoes;

    public enum Status { ABERTA, CONCLUIDA, CANCELADA }

    // ----- Regras -----

    public BigDecimal saldoKg() {
        BigDecimal s = quantidadeTotalKg.subtract(quantidadeEntregueKg);
        return s.signum() < 0 ? BigDecimal.ZERO : s;
    }

    public boolean aceitaNovaViagem() {
        return status == Status.ABERTA && saldoKg().signum() > 0;
    }

    /** Frete da viagem = tarifa x peso, em toneladas. Nunca digitado. */
    public BigDecimal freteDe(BigDecimal pesoKg) {
        return emToneladas(pesoKg).multiply(fretePorTonelada).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal valorCargaDe(BigDecimal pesoKg) {
        if (valorCargaPorTonelada == null) return null;
        return emToneladas(pesoKg).multiply(valorCargaPorTonelada).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal emToneladas(BigDecimal pesoKg) {
        return pesoKg.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    /**
     * Soma o que a viagem entregou. Fecha a demanda quando a conta bate — é o
     * "só termina quando completarem a quantidade".
     */
    public void registrarEntrega(BigDecimal pesoKg) {
        this.quantidadeEntregueKg = this.quantidadeEntregueKg.add(pesoKg);
        if (quantidadeEntregueKg.compareTo(quantidadeTotalKg) >= 0 && status == Status.ABERTA) {
            this.status = Status.CONCLUIDA;
            this.concluidaEm = LocalDateTime.now();
        }
    }

    /** Devolve o peso ao saldo quando uma viagem da demanda é cancelada. */
    public void estornarEntrega(BigDecimal pesoKg) {
        this.quantidadeEntregueKg = this.quantidadeEntregueKg.subtract(pesoKg);
        if (quantidadeEntregueKg.signum() < 0) this.quantidadeEntregueKg = BigDecimal.ZERO;
        if (status == Status.CONCLUIDA && quantidadeEntregueKg.compareTo(quantidadeTotalKg) < 0) {
            this.status = Status.ABERTA;
            this.concluidaEm = null;
        }
    }

    public UUID getId() { return id; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer v) { this.numero = v; }
    public String getOrigem() { return origem; }
    public void setOrigem(String v) { this.origem = v; }
    public String getDestino() { return destino; }
    public void setDestino(String v) { this.destino = v; }
    public String getEmpresaRemetente() { return empresaRemetente; }
    public void setEmpresaRemetente(String v) { this.empresaRemetente = v; }
    public String getEmpresaDestinataria() { return empresaDestinataria; }
    public void setEmpresaDestinataria(String v) { this.empresaDestinataria = v; }
    public String getCarga() { return carga; }
    public void setCarga(String v) { this.carga = v; }
    public BigDecimal getQuantidadeTotalKg() { return quantidadeTotalKg; }
    public void setQuantidadeTotalKg(BigDecimal v) { this.quantidadeTotalKg = v; }
    public BigDecimal getQuantidadeEntregueKg() { return quantidadeEntregueKg; }
    public void setQuantidadeEntregueKg(BigDecimal v) { this.quantidadeEntregueKg = v; }
    public BigDecimal getFretePorTonelada() { return fretePorTonelada; }
    public void setFretePorTonelada(BigDecimal v) { this.fretePorTonelada = v; }
    public BigDecimal getValorCargaPorTonelada() { return valorCargaPorTonelada; }
    public void setValorCargaPorTonelada(BigDecimal v) { this.valorCargaPorTonelada = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public LocalDateTime getConcluidaEm() { return concluidaEm; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String v) { this.observacoes = v; }
    public java.time.LocalDate getPrazoEntrega() { return prazoEntrega; }
    public void setPrazoEntrega(java.time.LocalDate v) { this.prazoEntrega = v; }
    public java.util.Set<Caminhao> getCaminhoesPermitidos() { return caminhoesPermitidos; }
    public void setCaminhoesPermitidos(java.util.Set<Caminhao> v) { this.caminhoesPermitidos = v; }
    public java.util.Set<String> getTiposReboquePermitidos() { return tiposReboquePermitidos; }
    public void setTiposReboquePermitidos(java.util.Set<String> v) { this.tiposReboquePermitidos = v; }

    /** Passou do prazo e ainda não fechou. Não impede novas viagens — sinaliza. */
    public boolean estaAtrasada() {
        return prazoEntrega != null
                && status == Status.ABERTA
                && java.time.LocalDate.now().isAfter(prazoEntrega);
    }

    /**
     * Confere se o equipamento serve para esta carga. Quem decide é o gestor:
     * lista vazia significa "qualquer um", não "nenhum".
     */
    public void exigirEquipamentoPermitido(Caminhao caminhao, Carreta carreta) {
        if (!caminhoesPermitidos.isEmpty()
                && caminhoesPermitidos.stream().noneMatch(c -> c.getId().equals(caminhao.getId()))) {
            throw new IllegalStateException(
                    "O caminhão %s não está liberado para esta demanda.".formatted(caminhao.getPlaca()));
        }
        if (!tiposReboquePermitidos.isEmpty()) {
            if (carreta == null) {
                throw new IllegalStateException(
                        "Esta demanda exige reboque do tipo: " + String.join(", ", tiposReboquePermitidos) + ".");
            }
            if (tiposReboquePermitidos.stream().noneMatch(t -> t.equalsIgnoreCase(carreta.getTipo()))) {
                throw new IllegalStateException(
                        "Reboque %s não serve nesta demanda. Permitidos: %s."
                                .formatted(carreta.getTipo(), String.join(", ", tiposReboquePermitidos)));
            }
        }
    }
}
