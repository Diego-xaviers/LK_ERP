package com.lktransportes.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache em memória dos dados do job atual por steamId.
 * Alimentado pelo VtlogController a cada live-snapshot recebido.
 * Consumido pelo TelemetriaService para complementar pings sem cargo/cidade.
 */
@Component
public class VtlogJobCache {

    private final ConcurrentHashMap<String, DadosJob> cache = new ConcurrentHashMap<>();

    public void atualizar(String steamId, DadosJob dados) {
        if (dados != null) cache.put(steamId, dados);
        else cache.remove(steamId);
    }

    public Optional<DadosJob> buscar(String steamId) {
        return Optional.ofNullable(cache.get(steamId));
    }

    public record DadosJob(
            String cargaNome,
            String cidadeOrigem,
            String cidadeDestino,
            String empresaOrigem,
            String empresaDestino,
            Double massaKg,
            Integer distanciaKm
    ) {}
}
