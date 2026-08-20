package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Documento fictício gerado a partir de uma viagem (NF, CT-e ou MDF-e).
 *
 * IMPORTANTE — por que os dados estão COPIADOS aqui e não vêm por relacionamento:
 * a spec exige que o documento preserve o estado do momento em que foi gerado.
 * Se guardássemos apenas FK pra Caminhao/Usuario, trocar a placa de um caminhão
 * amanhã reescreveria documentos históricos. Então gravamos um snapshot.
 */
@Entity
@Table(name = "documentos_viagem")
public class DocumentoViagem {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "viagem_id")
    private Viagem viagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoDocumento tipo;

    /** Número sequencial interno por tipo de documento. */
    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 10)
    private String serie = "001";

    /**
     * Chave INTERNA de simulação. Não é chave fiscal e não tem vínculo com SEFAZ.
     * Serve só para identificar o documento e montar o link de consulta interna.
     */
    @Column(name = "chave_interna", nullable = false, unique = true, length = 60)
    private String chaveInterna;

    @Column(name = "gerado_em", nullable = false)
    private LocalDateTime geradoEm = LocalDateTime.now();

    // ---------- Snapshot: congelado no momento da geração ----------
    @Column(name = "snap_motorista", nullable = false)
    private String snapMotorista;

    @Column(name = "snap_caminhao", nullable = false)
    private String snapCaminhao;

    @Column(name = "snap_placa_caminhao", nullable = false)
    private String snapPlacaCaminhao;

    @Column(name = "snap_carreta")
    private String snapCarreta;

    @Column(name = "snap_placa_carreta")
    private String snapPlacaCarreta;

    @Column(name = "snap_remetente", nullable = false)
    private String snapRemetente;

    @Column(name = "snap_destinatario", nullable = false)
    private String snapDestinatario;

    @Column(name = "snap_origem", nullable = false)
    private String snapOrigem;

    @Column(name = "snap_destino", nullable = false)
    private String snapDestino;

    @Column(name = "snap_carga", nullable = false)
    private String snapCarga;

    @Column(name = "snap_peso_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal snapPesoKg;

    @Column(name = "snap_valor_carga", precision = 14, scale = 2)
    private BigDecimal snapValorCarga;

    @Column(name = "snap_valor_frete", precision = 14, scale = 2)
    private BigDecimal snapValorFrete;

    public enum TipoDocumento { NF, CTE, MDFE }

    /** Copia da viagem tudo o que o documento precisa preservar. */
    public static DocumentoViagem gerarDe(Viagem v, TipoDocumento tipo, int numero, String chaveInterna) {
        DocumentoViagem d = new DocumentoViagem();
        d.viagem = v;
        d.tipo = tipo;
        d.numero = numero;
        d.chaveInterna = chaveInterna;
        d.snapMotorista = v.getMotorista().getNome();
        d.snapCaminhao = v.getCaminhao().getMarca() + " " + v.getCaminhao().getModelo();
        d.snapPlacaCaminhao = v.getCaminhao().getPlaca();
        if (v.getCarreta() != null) {
            d.snapCarreta = v.getCarreta().getTipo();
            d.snapPlacaCarreta = v.getCarreta().getPlaca();
        }
        d.snapRemetente = v.getEmpresaRemetente();
        d.snapDestinatario = v.getEmpresaDestinataria();
        d.snapOrigem = v.getOrigem();
        d.snapDestino = v.getDestino();
        d.snapCarga = v.getCarga();
        d.snapPesoKg = v.getPesoKg();
        d.snapValorCarga = v.getValorCarga();
        d.snapValorFrete = v.getValorFrete();
        return d;
    }

    public UUID getId() { return id; }
    public Viagem getViagem() { return viagem; }
    public TipoDocumento getTipo() { return tipo; }
    public Integer getNumero() { return numero; }
    public String getSerie() { return serie; }
    public String getChaveInterna() { return chaveInterna; }
    public LocalDateTime getGeradoEm() { return geradoEm; }
    public String getSnapMotorista() { return snapMotorista; }
    public String getSnapCaminhao() { return snapCaminhao; }
    public String getSnapPlacaCaminhao() { return snapPlacaCaminhao; }
    public String getSnapCarreta() { return snapCarreta; }
    public String getSnapPlacaCarreta() { return snapPlacaCarreta; }
    public String getSnapRemetente() { return snapRemetente; }
    public String getSnapDestinatario() { return snapDestinatario; }
    public String getSnapOrigem() { return snapOrigem; }
    public String getSnapDestino() { return snapDestino; }
    public String getSnapCarga() { return snapCarga; }
    public BigDecimal getSnapPesoKg() { return snapPesoKg; }
    public BigDecimal getSnapValorCarga() { return snapValorCarga; }
    public BigDecimal getSnapValorFrete() { return snapValorFrete; }
}
