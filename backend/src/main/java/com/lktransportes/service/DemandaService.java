package com.lktransportes.service;

import com.lktransportes.dto.*;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DemandaService {

    private final DemandaRepository demandas;
    private final ViagemRepository viagens;
    private final UsuarioRepository usuarios;
    private final CaminhaoRepository caminhoes;
    private final CarretaRepository carretas;
    private final CnhService cnhs;

    public DemandaService(DemandaRepository demandas, ViagemRepository viagens,
                          UsuarioRepository usuarios, CaminhaoRepository caminhoes,
                          CarretaRepository carretas, CnhService cnhs) {
        this.demandas = demandas;
        this.viagens = viagens;
        this.usuarios = usuarios;
        this.caminhoes = caminhoes;
        this.carretas = carretas;
        this.cnhs = cnhs;
    }

    @Transactional
    public DemandaResponse criar(NovaDemandaRequest req) {
        Demanda d = new Demanda();
        d.setNumero(demandas.ultimoNumero() + 1);
        d.setOrigem(req.origem);
        d.setDestino(req.destino);
        d.setEmpresaRemetente(req.empresaRemetente);
        d.setEmpresaDestinataria(req.empresaDestinataria);
        d.setCarga(req.carga);
        d.setQuantidadeTotalKg(req.quantidadeTotalKg);
        d.setFretePorTonelada(req.fretePorTonelada);
        d.setValorCargaPorTonelada(req.valorCargaPorTonelada);
        d.setObservacoes(req.observacoes);
        d.setPrazoEntrega(req.prazoEntrega);
        if (req.caminhoesPermitidos != null && !req.caminhoesPermitidos.isEmpty()) {
            d.setCaminhoesPermitidos(new java.util.HashSet<>(caminhoes.findAllById(req.caminhoesPermitidos)));
        }
        if (req.tiposReboquePermitidos != null) {
            d.setTiposReboquePermitidos(new java.util.HashSet<>(req.tiposReboquePermitidos));
        }
        return DemandaResponse.de(demandas.save(d), BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<DemandaResponse> listar() {
        return comReservas(demandas.findAllByOrderByCriadaEmDesc());
    }

    @Transactional(readOnly = true)
    public List<DemandaResponse> abertas() {
        return comReservas(demandas.findByStatusOrderByCriadaEmDesc(Demanda.Status.ABERTA));
    }

    /** Uma consulta só para o peso reservado de todas as demandas da lista. */
    private List<DemandaResponse> comReservas(List<Demanda> lista) {
        if (lista.isEmpty()) return List.of();
        Map<UUID, BigDecimal> reservas = new HashMap<>();
        for (Object[] linha : viagens.pesoEmCursoPorDemanda(lista.stream().map(Demanda::getId).toList())) {
            reservas.put((UUID) linha[0], (BigDecimal) linha[1]);
        }
        return lista.stream()
                .map(d -> DemandaResponse.de(d, reservas.get(d.getId())))
                .toList();
    }

    @Transactional
    public DemandaResponse cancelar(UUID id) {
        Demanda d = demandas.findById(id).orElseThrow();
        if (d.getStatus() == Demanda.Status.CONCLUIDA) {
            throw new IllegalStateException("Demanda já concluída não pode ser cancelada.");
        }
        d.setStatus(Demanda.Status.CANCELADA);
        return DemandaResponse.de(demandas.save(d), reservado(id));
    }

    /**
     * Motorista pega uma fatia da demanda e sai com ela. Os valores da viagem
     * são calculados a partir da tarifa — não vêm do cliente.
     */
    @Transactional
    public ViagemResponse aceitar(UUID demandaId, UUID motoristaId, AceitarDemandaRequest req) {
        Demanda d = demandas.findById(demandaId).orElseThrow();

        // Sem CNH válida não pega carga — é o que faz a renovação importar.
        cnhs.exigirCnhValida(motoristaId);

        if (d.getStatus() == Demanda.Status.CANCELADA) {
            throw new IllegalStateException("Esta demanda foi cancelada.");
        }
        if (d.getStatus() == Demanda.Status.CONCLUIDA) {
            throw new IllegalStateException("Esta demanda já foi concluída.");
        }

        // Uma carga por vez: conta também as viagens ainda não iniciadas, senão
        // um motorista sozinho reservaria a demanda inteira em viagens paradas.
        List<Viagem> abertas = viagens.viagensAbertasDoMotorista(motoristaId);
        if (!abertas.isEmpty()) {
            throw new IllegalStateException(
                    "Você já tem a viagem #" + abertas.get(0).getNumero()
                    + " em aberto. Finalize antes de pegar outra carga.");
        }

        BigDecimal disponivel = disponivel(d);
        if (req.pesoKg.compareTo(disponivel) > 0) {
            throw new IllegalStateException(
                    "Sobrou só %s kg nesta demanda (contando as viagens em curso)."
                            .formatted(disponivel.stripTrailingZeros().toPlainString()));
        }

        Caminhao caminhao = caminhoes.findById(req.caminhaoId).orElseThrow();
        Carreta carreta = req.carretaId == null ? null : carretas.findById(req.carretaId).orElseThrow();
        if (!caminhao.podeSerUsadoPor(motoristaId)) {
            throw new IllegalStateException(
                    "O caminhão %s é de outro motorista.".formatted(caminhao.getPlaca()));
        }
        d.exigirEquipamentoPermitido(caminhao, carreta);

        Viagem v = new Viagem();
        v.setNumero(viagens.ultimoNumero() + 1);
        v.setDemanda(d);
        v.setOrigem(d.getOrigem());
        v.setDestino(d.getDestino());
        v.setEmpresaRemetente(d.getEmpresaRemetente());
        v.setEmpresaDestinataria(d.getEmpresaDestinataria());
        v.setCarga(d.getCarga());
        v.setPesoKg(req.pesoKg);
        v.setValorFrete(d.freteDe(req.pesoKg));
        v.setValorCarga(d.valorCargaDe(req.pesoKg));
        v.setMotorista(usuarios.findById(motoristaId).orElseThrow());
        v.setCaminhao(caminhao);
        if (carreta != null) {
            v.setCarreta(carreta);
        }

        return ViagemResponse.de(viagens.save(v), List.of());
    }

    private BigDecimal reservado(UUID demandaId) {
        BigDecimal r = viagens.pesoEmCursoDaDemanda(demandaId);
        return r == null ? BigDecimal.ZERO : r;
    }

    /** O que falta entregar menos o que já está comprometido por viagens em curso. */
    private BigDecimal disponivel(Demanda d) {
        BigDecimal saldo = d.saldoKg().subtract(reservado(d.getId()));
        return saldo.signum() < 0 ? BigDecimal.ZERO : saldo;
    }

    @Transactional(readOnly = true)
    public DemandaResponse buscar(UUID id) {
        return DemandaResponse.de(demandas.findById(id).orElseThrow(), reservado(id));
    }
}
