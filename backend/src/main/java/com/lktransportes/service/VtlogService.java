package com.lktransportes.service;

import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class VtlogService {

    private final PerfilRepository perfis;
    private final ViagemRepository viagens;
    private final TelemetriaViagemRepository telemetrias;

    @Value("${lk.vtlog-secret:}")
    private String vtlogSecret;

    public VtlogService(PerfilRepository perfis, ViagemRepository viagens,
                        TelemetriaViagemRepository telemetrias) {
        this.perfis = perfis;
        this.viagens = viagens;
        this.telemetrias = telemetrias;
    }

    public void validarSegredo(String cabecalho) {
        if (vtlogSecret.isBlank() || !vtlogSecret.equals(cabecalho)) {
            throw new SecurityException("Segredo inválido.");
        }
    }

    @Transactional
    public Viagem registrarEntrega(EntregaVtlog req) {
        // Idempotência: job já registrado retorna a viagem existente
        viagens.findByVtlogJobId(req.jobId()).ifPresent(v -> {
            throw new IllegalStateException("Job " + req.jobId() + " já registrado na viagem #" + v.getNumero() + ".");
        });

        Perfil perfil = perfis.findBySteamId(req.steamId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Steam ID não encontrado. O motorista precisa cadastrar o Steam ID no perfil."));
        Usuario motorista = perfil.getUsuario();

        Caminhao caminhao = viagens.findFirstByMotoristaIdOrderByNumeroDesc(motorista.getId())
                .map(Viagem::getCaminhao)
                .orElseThrow(() -> new IllegalStateException(
                        "Motorista não tem viagem anterior. Cadastre uma viagem manual primeiro para vincular o caminhão."));

        Viagem v = new Viagem();
        v.setNumero(viagens.ultimoNumero() + 1);
        v.setOrigem(req.origem());
        v.setDestino(req.destino());
        v.setEmpresaRemetente(req.empresaOrigem());
        v.setEmpresaDestinataria(req.empresaDestino());
        v.setCarga(req.carga());
        v.setPesoKg(req.pesoKg() != null ? req.pesoKg() : BigDecimal.ZERO);
        v.setValorFrete(req.valorFrete());
        v.setMotorista(motorista);
        v.setCaminhao(caminhao);
        v.setStatus(StatusViagem.CONCLUIDA);
        v.setFinalizadaEm(LocalDateTime.now());
        v.setConferencia(Viagem.Conferencia.RETIDA);
        v.setVtlogJobId(req.jobId());
        viagens.save(v);

        TelemetriaViagem tel = new TelemetriaViagem();
        tel.setViagem(v);
        tel.setDistanciaConfirmadaKm(req.distanciaKm());
        tel.setLitrosAbastecidos(req.combustivelGastoL());
        tel.setDanoAtualPct(req.danoPct());
        tel.setDanoRegistradoPct(req.danoPct());
        telemetrias.save(tel);

        return v;
    }

    public record EntregaVtlog(
            String jobId,
            String steamId,
            String origem,
            String destino,
            String empresaOrigem,
            String empresaDestino,
            String carga,
            BigDecimal pesoKg,
            Double distanciaKm,
            Double combustivelGastoL,
            Double danoPct,
            BigDecimal valorFrete
    ) {}
}
