package com.lktransportes.service;

import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class VtlogService {

    private final PerfilRepository perfis;
    private final ViagemRepository viagens;
    private final TelemetriaViagemRepository telemetrias;
    private final EventoViagemRepository eventos;
    private final ViagemService viagemService;

    @Value("${lk.vtlog-secret:}")
    private String vtlogSecret;

    public VtlogService(PerfilRepository perfis, ViagemRepository viagens,
                        TelemetriaViagemRepository telemetrias, EventoViagemRepository eventos,
                        ViagemService viagemService) {
        this.perfis = perfis;
        this.viagens = viagens;
        this.telemetrias = telemetrias;
        this.eventos = eventos;
        this.viagemService = viagemService;
    }

    /** Chamado pelo VtlogController quando detecta aumento de fines no snapshot ao vivo. */
    @Transactional
    public void registrarMultaVtlog(String steamId, double valor) {
        Optional<Perfil> perfilOpt = perfis.findBySteamId(steamId);
        if (perfilOpt.isEmpty()) return;

        Usuario motorista = perfilOpt.get().getUsuario();
        viagens.buscarAtivaSimples(motorista.getId(), StatusViagem.EM_ANDAMENTO).ifPresent(v -> {
            Multa multa = new Multa();
            multa.setViagem(v);
            multa.setMotivo("Multa detectada automaticamente via VTLog");
            multa.setValor(BigDecimal.valueOf(valor).setScale(2, java.math.RoundingMode.HALF_UP));
            multa.setOrigem(EventoViagem.Origem.TELEMETRIA);
            eventos.save(multa);
        });
    }

    public void validarSegredo(String cabecalho) {
        if (vtlogSecret.isBlank() || !vtlogSecret.equals(cabecalho)) {
            throw new SecurityException("Segredo inválido.");
        }
    }

    @Transactional
    public Viagem registrarEntrega(EntregaVtlog req) {
        // Idempotência: job já registrado retorna a viagem existente
        Optional<Viagem> existentePorJob = viagens.findByVtlogJobId(req.jobId());
        if (existentePorJob.isPresent()) {
            throw new IllegalStateException(
                    "Job " + req.jobId() + " já registrado na viagem #" + existentePorJob.get().getNumero() + ".");
        }

        Perfil perfil = perfis.findBySteamId(req.steamId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Steam ID não encontrado. O motorista precisa cadastrar o Steam ID no perfil."));
        Usuario motorista = perfil.getUsuario();

        // Se existe viagem EM_ANDAMENTO, enriquece com dados do VTLog e conclui corretamente
        Optional<Viagem> viagemAtiva = viagens.buscarAtivaSimples(motorista.getId(), StatusViagem.EM_ANDAMENTO);
        if (viagemAtiva.isPresent()) {
            return concluirViagemAtiva(viagemAtiva.get(), req);
        }

        // Sem viagem ativa: cria do zero (entrega não precedida de agente PS1)
        return criarViagemConcluida(motorista, req);
    }

    /**
     * Enriquece a viagem EM_ANDAMENTO com os dados definitivos do VTLog e
     * passa pelo caminho completo de finalização (conferência, crédito, etc.).
     */
    private Viagem concluirViagemAtiva(Viagem v, EntregaVtlog req) {
        // Vincula ao job VTLog para garantir idempotência futura
        v.setVtlogJobId(req.jobId());

        // Preenche campos ausentes com o que o VTLog sabe
        if (req.valorFrete() != null && (v.getValorFrete() == null || v.getValorFrete().signum() == 0)) {
            v.setValorFrete(req.valorFrete());
        }
        if (req.pesoKg() != null && (v.getPesoKg() == null || v.getPesoKg().signum() == 0)) {
            v.setPesoKg(req.pesoKg());
        }
        if (req.origem() != null && !req.origem().isBlank() && (v.getOrigem() == null || v.getOrigem().equals("—"))) {
            v.setOrigem(req.origem());
        }
        if (req.destino() != null && !req.destino().isBlank() && (v.getDestino() == null || v.getDestino().equals("—"))) {
            v.setDestino(req.destino());
        }
        if (req.empresaOrigem() != null && !req.empresaOrigem().isBlank() && "—".equals(v.getEmpresaRemetente())) {
            v.setEmpresaRemetente(req.empresaOrigem());
        }
        if (req.empresaDestino() != null && !req.empresaDestino().isBlank() && "—".equals(v.getEmpresaDestinataria())) {
            v.setEmpresaDestinataria(req.empresaDestino());
        }
        if (req.carga() != null && !req.carga().isBlank() && (v.getCarga() == null || v.getCarga().isBlank())) {
            v.setCarga(req.carga());
        }
        viagens.save(v);

        // Enriquece a TelemetriaViagem com a distância real e dados de consumo do VTLog
        telemetrias.findByViagemId(v.getId()).ifPresent(tv -> {
            if (req.distanciaKm() != null && tv.getDistanciaConfirmadaKm() == null) {
                tv.setDistanciaConfirmadaKm(req.distanciaKm());
            }
            if (req.danoPct() != null) {
                tv.setDanoAtualPct(req.danoPct());
                if (tv.getDanoRegistradoPct() == null) tv.setDanoRegistradoPct(req.danoPct());
            }
            telemetrias.save(tv);
        });

        // Finaliza pelo caminho completo: conferência, crédito de frete, etc.
        viagemService.finalizar(v.getId(), null, null);

        return viagens.findById(v.getId()).orElse(v);
    }

    /**
     * Cria uma viagem já concluída quando não havia viagem ativa no momento da entrega.
     * (Motorista não usou o agente PS1 ou entregou antes do ping ser processado.)
     */
    private Viagem criarViagemConcluida(Usuario motorista, EntregaVtlog req) {
        Caminhao caminhao = viagens.findFirstByMotoristaIdOrderByNumeroDesc(motorista.getId())
                .map(Viagem::getCaminhao)
                .orElseThrow(() -> new IllegalStateException(
                        "Motorista não tem viagem anterior. Cadastre uma viagem manual primeiro para vincular o caminhão."));

        Viagem v = new Viagem();
        v.setNumero(viagens.ultimoNumero() + 1);
        v.setOrigem(req.origem() != null ? req.origem() : "—");
        v.setDestino(req.destino() != null ? req.destino() : "—");
        v.setEmpresaRemetente(req.empresaOrigem() != null ? req.empresaOrigem() : "—");
        v.setEmpresaDestinataria(req.empresaDestino() != null ? req.empresaDestino() : "—");
        v.setCarga(req.carga() != null ? req.carga() : "—");
        v.setPesoKg(req.pesoKg() != null ? req.pesoKg() : BigDecimal.ZERO);
        v.setValorFrete(req.valorFrete());
        v.setMotorista(motorista);
        v.setCaminhao(caminhao);
        v.setVtlogJobId(req.jobId());
        // Inicia direto para poder chamar finalizar() pelo caminho completo
        v.iniciar();
        viagens.save(v);

        TelemetriaViagem tel = new TelemetriaViagem();
        tel.setViagem(v);
        tel.setDistanciaConfirmadaKm(req.distanciaKm());
        tel.setLitrosAbastecidos(req.combustivelGastoL() != null ? req.combustivelGastoL() : 0d);
        tel.setDanoAtualPct(req.danoPct());
        tel.setDanoRegistradoPct(req.danoPct());
        telemetrias.save(tel);

        viagemService.finalizar(v.getId(), null, null);

        return viagens.findById(v.getId()).orElse(v);
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
