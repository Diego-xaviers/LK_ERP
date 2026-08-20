package com.lktransportes.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Um request só para os eventos simples (pedágio, multa, manutenção, ocorrência). */
public class EventoRequest {
    public BigDecimal valor;
    public LocalDateTime ocorridoEm;
    public String observacao;

    // pedágio
    public String local;
    // multa
    public String motivo;
    // manutenção
    public UUID oficinaId;
    public String servico;
    // ocorrência
    public String titulo;
    public String descricao;
    // evidência (multa/ocorrência)
    public String evidenciaUrl;
}
