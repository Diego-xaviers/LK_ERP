package com.lktransportes.service;

import com.lktransportes.dto.TelemetriaPing;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recebe os pings do agente e transforma em coisa útil: estado ao vivo,
 * acumulado da viagem, eventos automáticos e sinais de conferência.
 */
@Service
public class TelemetriaService {

    /** Nome do posto usado quando a telemetria detecta abastecimento fora de posto cadastrado. */
    static final String POSTO_NAO_IDENTIFICADO = "Posto não identificado (telemetria)";

    /** Abaixo disso é oscilação de leitura, não abastecimento de verdade. */
    private static final double LITROS_MINIMOS = 5.0;
    /** Dano precisa subir isso pra virar ocorrência — evita evento a cada arranhão. */
    private static final double DANO_MINIMO_PCT = 5.0;
    /** Distância entre dois pings que nenhum caminhão faz dirigindo (~2 s). */
    private static final double SALTO_METROS = 1000.0;

    private final UsuarioRepository usuarios;
    private final ViagemRepository viagens;
    private final TelemetriaSessaoRepository sessoes;
    private final TelemetriaViagemRepository telemetriaViagens;
    private final EventoViagemRepository eventos;
    private final PostoRepository postos;
    private final MapaService mapa;

    public TelemetriaService(UsuarioRepository usuarios, ViagemRepository viagens,
                             TelemetriaSessaoRepository sessoes, TelemetriaViagemRepository telemetriaViagens,
                             EventoViagemRepository eventos, PostoRepository postos,
                             MapaService mapa) {
        this.usuarios = usuarios;
        this.viagens = viagens;
        this.sessoes = sessoes;
        this.telemetriaViagens = telemetriaViagens;
        this.eventos = eventos;
        this.postos = postos;
        this.mapa = mapa;
    }

    // ------------------------------------------------------------------
    // Token de pareamento
    // ------------------------------------------------------------------

    /** Gera (ou regenera) o segredo do agente. Regenerar invalida o pacote antigo. */
    @Transactional
    public String gerarToken(UUID motoristaId) {
        Usuario u = usuarios.findById(motoristaId).orElseThrow();
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        u.setTokenTelemetria(Base64.getUrlEncoder().withoutPadding().encodeToString(b));
        usuarios.save(u);
        return u.getTokenTelemetria();
    }

    @Transactional(readOnly = true)
    public String tokenDe(UUID motoristaId) {
        return usuarios.findById(motoristaId).orElseThrow().getTokenTelemetria();
    }

    public Usuario autenticar(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException();
        }
        return usuarios.findByTokenTelemetria(token).orElseThrow(TokenInvalidoException::new);
    }

    /** 401 em vez de 500 quando o agente está com token velho. */
    public static class TokenInvalidoException extends RuntimeException {
        public TokenInvalidoException() { super("Token de telemetria inválido."); }
    }

    // ------------------------------------------------------------------
    // Ping
    // ------------------------------------------------------------------

    @Transactional
    public TelemetriaSessao registrar(Usuario motorista, TelemetriaPing ping) {
        TelemetriaSessao sessao = sessoes.findByMotoristaId(motorista.getId())
                .orElseGet(() -> {
                    TelemetriaSessao nova = new TelemetriaSessao();
                    nova.setMotorista(motorista);
                    return nova;
                });

        Double posAnteriorX = sessao.getPosX();
        Double posAnteriorZ = sessao.getPosZ();

        aplicar(sessao, ping);
        sessoes.save(sessao);

        // Consulta enxuta de propósito: o ping chega a cada 2 s e não precisa dos eventos.
        viagens.buscarAtivaSimples(motorista.getId(), StatusViagem.EM_ANDAMENTO)
                .ifPresent(viagem -> alimentarViagem(viagem, ping, posAnteriorX, posAnteriorZ));

        return sessao;
    }

    private void aplicar(TelemetriaSessao s, TelemetriaPing p) {
        s.setAtualizadoEm(java.time.LocalDateTime.now());
        s.setVelocidadeKmh(p.velocidadeKmh);
        s.setRpm(p.rpm);
        s.setMarcha(p.marcha);
        s.setCombustivelL(p.combustivelL);
        s.setCombustivelCapacidadeL(p.combustivelCapacidadeL);
        s.setOdometroKm(p.odometroKm);
        s.setDanoMotorPct(p.desgasteMotorPct);
        s.setDanoCambioPct(p.desgasteCambioPct);
        s.setDanoCabinePct(p.desgasteCabinePct);
        s.setDanoChassiPct(p.desgasteChassiPct);
        s.setDanoRodasPct(p.desgasteRodasPct);
        s.setDanoCargaPct(p.desgasteCargaPct);
        s.setPosX(p.posX);
        s.setPosY(p.posY);
        s.setPosZ(p.posZ);
        s.setPilotoAutomatico(p.pilotoAutomatico);
        s.setPausado(p.pausado);
        s.setEmServico(p.emServico);
        s.setCargaNome(p.cargaNome);
        s.setCargaMassaKg(p.cargaMassaKg);
        s.setCidadeOrigem(p.cidadeOrigem);
        s.setCidadeDestino(p.cidadeDestino);
        s.setEmpresaOrigem(p.empresaOrigem);
        s.setEmpresaDestino(p.empresaDestino);
        s.setDistanciaPlanejadaKm(p.distanciaPlanejadaKm);
        s.setPlacaCaminhao(p.placaCaminhao);
        s.setModeloCaminhao(p.modeloCaminhao);
    }

    // ------------------------------------------------------------------
    // Acumulado da viagem + eventos automáticos
    // ------------------------------------------------------------------

    private void alimentarViagem(Viagem viagem, TelemetriaPing p, Double posAnteriorX, Double posAnteriorZ) {
        TelemetriaViagem tv = telemetriaViagens.findByViagemId(viagem.getId())
                .orElseGet(() -> {
                    TelemetriaViagem nova = new TelemetriaViagem();
                    nova.setViagem(viagem);
                    return nova;
                });

        double danoAgora = p.danoCaminhaoPct();

        // Primeira leitura da viagem define as linhas de base.
        if (tv.getOdometroInicialKm() == null && p.odometroKm != null) {
            tv.setOdometroInicialKm(p.odometroKm);
        }
        if (tv.getCombustivelInicialL() == null && p.combustivelL != null) {
            tv.setCombustivelInicialL(p.combustivelL);
        }
        if (tv.getDanoInicialPct() == null) {
            tv.setDanoInicialPct(danoAgora);
            tv.setDanoRegistradoPct(danoAgora);
        }

        if (p.odometroKm != null) tv.setOdometroAtualKm(p.odometroKm);
        if (p.combustivelL != null) tv.setCombustivelAtualL(p.combustivelL);
        tv.setDanoAtualPct(danoAgora);

        // O que o jogo diz que está no engate — base da conferência depois.
        if (p.cargaNome != null && !p.cargaNome.isBlank()) tv.setCargaJogo(p.cargaNome);
        if (p.cargaMassaKg != null && p.cargaMassaKg > 0) tv.setPesoJogoKg(p.cargaMassaKg);
        Double rodado = tv.distanciaPercorridaKm();
        if (rodado != null) tv.setDistanciaConfirmadaKm(rodado);

        detectarAbastecimento(viagem, tv, p);
        detectarAvaria(viagem, tv, danoAgora);
        registrarSinais(tv, p, posAnteriorX, posAnteriorZ);
        conferirComDeclarado(viagem, tv, p);
        mapa.observar(tv, p);

        telemetriaViagens.save(tv);
    }

    /**
     * O jogo marca `refuel` enquanto a bomba está ligada. Guarda o nível ao começar
     * e fecha o evento na borda de descida, com os litros que realmente entraram.
     */
    private void detectarAbastecimento(Viagem viagem, TelemetriaViagem tv, TelemetriaPing p) {
        boolean abastecendoAgora = Boolean.TRUE.equals(p.abastecendo);

        if (abastecendoAgora && !tv.getAbastecendo()) {
            tv.setAbastecendo(true);
            tv.setCombustivelAoIniciarAbastecimento(p.combustivelL);
            return;
        }
        if (!abastecendoAgora && tv.getAbastecendo()) {
            tv.setAbastecendo(false);
            Double inicio = tv.getCombustivelAoIniciarAbastecimento();
            tv.setCombustivelAoIniciarAbastecimento(null);
            if (inicio == null || p.combustivelL == null) return;

            double litros = p.combustivelL - inicio;
            if (litros < LITROS_MINIMOS) return;

            Abastecimento a = new Abastecimento();
            a.setViagem(viagem);
            a.setPosto(postoDaTelemetria());
            a.setLitros(BigDecimal.valueOf(litros).setScale(3, RoundingMode.HALF_UP));
            a.setOrigem(EventoViagem.Origem.TELEMETRIA);
            a.setObservacao("Detectado pela telemetria do jogo. Sem valor por litro — edite se quiser lançar o custo.");
            eventos.save(a);

            tv.setLitrosAbastecidos(tv.getLitrosAbastecidos() + litros);
        }
    }

    private void detectarAvaria(Viagem viagem, TelemetriaViagem tv, double danoAgora) {
        double jaRegistrado = tv.getDanoRegistradoPct() == null ? 0 : tv.getDanoRegistradoPct();
        if (danoAgora - jaRegistrado < DANO_MINIMO_PCT) return;

        Ocorrencia o = new Ocorrencia();
        o.setViagem(viagem);
        o.setTitulo("Avaria detectada pela telemetria");
        o.setDescricao(String.format(
                "O dano do caminhão subiu de %.1f%% para %.1f%% durante a viagem.", jaRegistrado, danoAgora));
        o.setOrigem(EventoViagem.Origem.TELEMETRIA);
        eventos.save(o);

        tv.setDanoRegistradoPct(danoAgora);
    }

    /** Sinais de conferência — não bloqueiam nada, só ficam registrados pro gestor. */
    private void registrarSinais(TelemetriaViagem tv, TelemetriaPing p, Double anteriorX, Double anteriorZ) {
        if (Boolean.TRUE.equals(p.pilotoAutomatico)) tv.setUsouPilotoAutomatico(true);
        if (Boolean.TRUE.equals(p.estacionamentoAutomatico)) tv.setUsouEstacionamentoAutomatico(true);

        if (anteriorX != null && anteriorZ != null && p.posX != null && p.posZ != null) {
            double dx = p.posX - anteriorX;
            double dz = p.posZ - anteriorZ;
            if (Math.sqrt(dx * dx + dz * dz) > SALTO_METROS) {
                tv.setSaltos(tv.getSaltos() + 1);
            }
        }
    }

    /** Compara o que o motorista digitou na viagem com o que o jogo está reportando. */
    private void conferirComDeclarado(Viagem viagem, TelemetriaViagem tv, TelemetriaPing p) {
        List<String> achados = new ArrayList<>();
        if (divergem(viagem.getOrigem(), p.cidadeOrigem)) {
            achados.add("origem declarada \"" + viagem.getOrigem() + "\" e o jogo reporta \"" + p.cidadeOrigem + "\"");
        }
        if (divergem(viagem.getDestino(), p.cidadeDestino)) {
            achados.add("destino declarado \"" + viagem.getDestino() + "\" e o jogo reporta \"" + p.cidadeDestino + "\"");
        }
        String texto = achados.isEmpty() ? null : String.join("; ", achados);
        if (texto != null && texto.length() > 500) texto = texto.substring(0, 500);
        tv.setDivergencias(texto);
    }

    /** Só acusa divergência quando os dois lados têm valor e nenhum contém o outro. */
    private boolean divergem(String declarado, String doJogo) {
        if (declarado == null || doJogo == null || declarado.isBlank() || doJogo.isBlank()) return false;
        String a = normalizar(declarado);
        String b = normalizar(doJogo);
        return !a.contains(b) && !b.contains(a);
    }

    private String normalizar(String s) {
        String semAcento = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private Posto postoDaTelemetria() {
        return postos.findByNome(POSTO_NAO_IDENTIFICADO).orElseGet(() -> {
            Posto p = new Posto();
            p.setNome(POSTO_NAO_IDENTIFICADO);
            p.setCidade("—");
            p.setEstado("--");
            p.setAtivo(false);   // fora das listas de seleção do motorista
            return postos.save(p);
        });
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<TelemetriaSessao> sessaoDe(UUID motoristaId) {
        return sessoes.findByMotoristaId(motoristaId);
    }

    @Transactional(readOnly = true)
    public Integer numeroDaViagemAtiva(UUID motoristaId) {
        return viagens.buscarAtivaSimples(motoristaId, StatusViagem.EM_ANDAMENTO)
                .map(Viagem::getNumero).orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<TelemetriaViagem> daViagem(UUID viagemId) {
        return telemetriaViagens.findByViagemId(viagemId);
    }

    /** De quem é a viagem — usado para decidir quem pode ver a telemetria dela. */
    @Transactional(readOnly = true)
    public UUID donoDaViagem(UUID viagemId) {
        return viagens.findById(viagemId).orElseThrow().getMotorista().getId();
    }
}
