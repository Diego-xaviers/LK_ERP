package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Centro do domínio. Todo evento (abastecimento, pedágio, multa, manutenção,
 * ocorrência) e todo documento pertencem a uma viagem.
 *
 * Nota de arquitetura: os eventos herdam de EventoViagem justamente pra que uma
 * futura integração de telemetria do ETS2 possa criar eventos automaticamente
 * sem alterar o resto do modelo.
 */
@Entity
@Table(name = "viagens")
public class Viagem {

    @Id @GeneratedValue
    private UUID id;

    /** Número sequencial exibido ao motorista (ex.: #2841). */
    @Column(nullable = false, unique = true)
    private Integer numero;

    // ----- Rota -----
    @Column(nullable = false)
    private String origem;

    @Column(nullable = false)
    private String destino;

    @Column(name = "empresa_remetente", nullable = false)
    private String empresaRemetente;

    @Column(name = "empresa_destinatoria", nullable = false)
    private String empresaDestinataria;

    // ----- Carga -----
    @Column(nullable = false)
    private String carga;

    @Column(name = "peso_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal pesoKg;

    @Column(name = "valor_carga", precision = 14, scale = 2)
    private BigDecimal valorCarga;

    @Column(name = "valor_frete", precision = 14, scale = 2)
    private BigDecimal valorFrete;

    // ----- Quem/com que -----
    @ManyToOne(optional = false) @JoinColumn(name = "motorista_id")
    private Usuario motorista;

    @ManyToOne(optional = false) @JoinColumn(name = "caminhao_id")
    private Caminhao caminhao;

    @ManyToOne @JoinColumn(name = "carreta_id")
    private Carreta carreta;

    /**
     * De qual demanda esta viagem saiu. Nulo em viagem avulsa — e é justamente
     * essa diferença que diz se o valor do frete é confiável: quando vem da
     * demanda, o motorista não digitou o número.
     */
    @ManyToOne @JoinColumn(name = "demanda_id")
    private Demanda demanda;

    // ----- Ciclo de vida -----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusViagem status = StatusViagem.CRIADA;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    @Column(name = "iniciada_em")
    private LocalDateTime iniciadaEm;

    @Column(name = "finalizada_em")
    private LocalDateTime finalizadaEm;

    @Column(name = "observacao_final", length = 1000)
    private String observacaoFinal;

    /** Job ID do VTLog que originou esta viagem. Nulo em viagens manuais. */
    @Column(name = "vtlog_job_id", unique = true, length = 20)
    private String vtlogJobId;

    @Column(name = "houve_avaria")
    private Boolean houveAvaria;

    // ----- Conferência (decide se pontua e se pode ser paga) -----

    /**
     * Preenchida no fim da viagem. RETIDA = a telemetria não confirmou o que foi
     * declarado; a viagem existe e conta no histórico, mas não pontua nem pode
     * ser paga até um gestor liberar. O motorista não fica impedido de rodar.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Conferencia conferencia;

    @Column(name = "motivos_conferencia", length = 1000)
    private String motivosConferencia;

    /** Em que acerto esta viagem foi paga. Nulo = ainda não paga. */
    @ManyToOne @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @ManyToOne @JoinColumn(name = "liberada_por_id")
    private Usuario liberadaPor;

    @Column(name = "liberada_em")
    private LocalDateTime liberadaEm;

    @Column(name = "observacao_liberacao", length = 600)
    private String observacaoLiberacao;

    public enum Conferencia { APROVADA, RETIDA, LIBERADA }

    @OneToMany(mappedBy = "viagem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventoViagem> eventos = new ArrayList<>();

    // ----- Regras de domínio -----
    public void iniciar() {
        if (status != StatusViagem.CRIADA) {
            throw new IllegalStateException("Só é possível iniciar uma viagem recém-criada.");
        }
        this.status = StatusViagem.EM_ANDAMENTO;
        this.iniciadaEm = LocalDateTime.now();
    }

    public void finalizar(String observacaoFinal, Boolean houveAvaria) {
        if (status != StatusViagem.EM_ANDAMENTO) {
            throw new IllegalStateException("Só é possível finalizar uma viagem em andamento.");
        }
        this.status = StatusViagem.CONCLUIDA;
        this.finalizadaEm = LocalDateTime.now();
        this.observacaoFinal = observacaoFinal;
        this.houveAvaria = houveAvaria;
    }

    /** Soma de tudo que a viagem custou — usado no resumo de finalização. */
    public BigDecimal totalDespesas() {
        return eventos.stream()
                .map(EventoViagem::getValor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() { return id; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public String getEmpresaRemetente() { return empresaRemetente; }
    public void setEmpresaRemetente(String v) { this.empresaRemetente = v; }
    public String getEmpresaDestinataria() { return empresaDestinataria; }
    public void setEmpresaDestinataria(String v) { this.empresaDestinataria = v; }
    public String getCarga() { return carga; }
    public void setCarga(String carga) { this.carga = carga; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public void setPesoKg(BigDecimal pesoKg) { this.pesoKg = pesoKg; }
    public BigDecimal getValorCarga() { return valorCarga; }
    public void setValorCarga(BigDecimal v) { this.valorCarga = v; }
    public BigDecimal getValorFrete() { return valorFrete; }
    public void setValorFrete(BigDecimal v) { this.valorFrete = v; }
    public Usuario getMotorista() { return motorista; }
    public void setMotorista(Usuario m) { this.motorista = m; }
    public Caminhao getCaminhao() { return caminhao; }
    public void setCaminhao(Caminhao c) { this.caminhao = c; }
    public Carreta getCarreta() { return carreta; }
    public void setCarreta(Carreta c) { this.carreta = c; }
    public Demanda getDemanda() { return demanda; }
    public void setDemanda(Demanda d) { this.demanda = d; }
    public StatusViagem getStatus() { return status; }
    public void setStatus(StatusViagem s) { this.status = s; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public LocalDateTime getIniciadaEm() { return iniciadaEm; }
    public LocalDateTime getFinalizadaEm() { return finalizadaEm; }
    public void setFinalizadaEm(LocalDateTime v) { this.finalizadaEm = v; }
    public String getObservacaoFinal() { return observacaoFinal; }
    public String getVtlogJobId() { return vtlogJobId; }
    public void setVtlogJobId(String v) { this.vtlogJobId = v; }
    public Boolean getHouveAvaria() { return houveAvaria; }
    public List<EventoViagem> getEventos() { return eventos; }
    public Pagamento getPagamento() { return pagamento; }
    public void setPagamento(Pagamento p) { this.pagamento = p; }
    public Conferencia getConferencia() { return conferencia; }
    public void setConferencia(Conferencia c) { this.conferencia = c; }
    public String getMotivosConferencia() { return motivosConferencia; }
    public void setMotivosConferencia(String v) { this.motivosConferencia = v; }
    public Usuario getLiberadaPor() { return liberadaPor; }
    public LocalDateTime getLiberadaEm() { return liberadaEm; }
    public String getObservacaoLiberacao() { return observacaoLiberacao; }

    /** Só pontua e só pode ser paga quando a conferência não está segurando. */
    public boolean liberadaParaPagamento() {
        return status == StatusViagem.CONCLUIDA
                && (conferencia == Conferencia.APROVADA || conferencia == Conferencia.LIBERADA);
    }

    /** Gestor assume a viagem apesar da divergência. Fica registrado quem e quando. */
    public void liberarConferencia(Usuario gestor, String observacao) {
        if (conferencia != Conferencia.RETIDA) {
            throw new IllegalStateException("Esta viagem não está retida.");
        }
        this.conferencia = Conferencia.LIBERADA;
        this.liberadaPor = gestor;
        this.liberadaEm = LocalDateTime.now();
        this.observacaoLiberacao = observacao;
    }
}
