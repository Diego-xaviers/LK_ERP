package com.lktransportes.service;

import com.lktransportes.dto.TelemetriaPing;
import com.lktransportes.model.CidadeMapa;
import com.lktransportes.model.MapaConhecido;
import com.lktransportes.model.TelemetriaViagem;
import com.lktransportes.repository.CidadeMapaRepository;
import com.lktransportes.repository.MapaConhecidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Garante que a viagem aconteceu no mapa certo.
 *
 * A ideia: o mod do RBR usa ids de cidade próprios, diferentes dos da Europa
 * base e de qualquer outro mapa. Em vez de embutir essa lista no código — que
 * envelheceria a cada atualização do mod —, o sistema aprende com as primeiras
 * viagens reais e o gestor tranca depois de conferir.
 */
@Service
public class MapaService {

    private final MapaConhecidoRepository mapas;
    private final CidadeMapaRepository cidades;

    public MapaService(MapaConhecidoRepository mapas, CidadeMapaRepository cidades) {
        this.mapas = mapas;
        this.cidades = cidades;
    }

    @Transactional
    public MapaConhecido config() {
        return mapas.findAll().stream().findFirst().orElseGet(() -> mapas.save(new MapaConhecido()));
    }

    @Transactional(readOnly = true)
    public List<CidadeMapa> cidadesConhecidas() {
        return cidades.findAllByOrderByNomeAsc();
    }

    /**
     * Chamado a cada ping de viagem ativa. Em APRENDENDO coleta; em ATIVO só
     * marca se a posição saiu da área conhecida.
     */
    @Transactional
    public void observar(TelemetriaViagem tv, TelemetriaPing p) {
        if (p.cidadeOrigemId != null && !p.cidadeOrigemId.isBlank()) {
            tv.setCidadeOrigemId(p.cidadeOrigemId);
        }
        if (p.cidadeDestinoId != null && !p.cidadeDestinoId.isBlank()) {
            tv.setCidadeDestinoId(p.cidadeDestinoId);
        }

        MapaConhecido m = config();

        if (m.getModo() == MapaConhecido.Modo.APRENDENDO) {
            aprenderCidade(p.cidadeOrigemId, p.cidadeOrigem);
            aprenderCidade(p.cidadeDestinoId, p.cidadeDestino);
            if (p.posX != null && p.posZ != null) {
                m.registrarPosicao(p.posX, p.posZ);
                mapas.save(m);
            }
        } else if (p.posX != null && p.posZ != null && !m.dentroDaArea(p.posX, p.posZ)) {
            tv.setForaDaArea(true);
        }
    }

    private void aprenderCidade(String idJogo, String nome) {
        if (idJogo == null || idJogo.isBlank()) return;
        cidades.findByIdJogo(idJogo).ifPresentOrElse(
                c -> { c.marcarVista(nome); cidades.save(c); },
                () -> {
                    CidadeMapa nova = new CidadeMapa();
                    nova.setIdJogo(idJogo);
                    nova.setNome(nome);
                    cidades.save(nova);
                });
    }

    /**
     * Motivos para reter a viagem por causa do mapa. Só vale em modo ATIVO —
     * enquanto está aprendendo, não há referência para acusar ninguém.
     */
    @Transactional(readOnly = true)
    public void conferir(TelemetriaViagem tv, List<String> motivos) {
        MapaConhecido m = config();
        if (m.getModo() != MapaConhecido.Modo.ATIVO) return;

        desconhecida(tv.getCidadeOrigemId(), "origem", motivos);
        desconhecida(tv.getCidadeDestinoId(), "destino", motivos);

        if (tv.getForaDaArea()) {
            motivos.add("O caminhão esteve fora da área conhecida do mapa — provavelmente outro mapa.");
        }
    }

    private void desconhecida(String idJogo, String qual, List<String> motivos) {
        if (idJogo == null || idJogo.isBlank()) return;
        if (cidades.findByIdJogo(idJogo).isEmpty()) {
            motivos.add("Cidade de %s \"%s\" não pertence ao mapa da transportadora.".formatted(qual, idJogo));
        }
    }

    // ----- Gestão -----

    @Transactional
    public MapaConhecido trancar() {
        MapaConhecido m = config();
        if (cidades.count() == 0) {
            throw new IllegalStateException(
                    "Nenhuma cidade foi aprendida ainda. Rode ao menos uma viagem no mapa antes de trancar.");
        }
        m.setModo(MapaConhecido.Modo.ATIVO);
        return mapas.save(m);
    }

    @Transactional
    public MapaConhecido voltarAAprender() {
        MapaConhecido m = config();
        m.setModo(MapaConhecido.Modo.APRENDENDO);
        return mapas.save(m);
    }

    /** Remove uma cidade aprendida por engano (viagem feita no mapa errado). */
    @Transactional
    public void esquecerCidade(UUID id) {
        cidades.deleteById(id);
    }

    @Transactional
    public MapaConhecido definirMargem(Double metros) {
        MapaConhecido m = config();
        m.setMargemMetros(metros);
        return mapas.save(m);
    }
}
