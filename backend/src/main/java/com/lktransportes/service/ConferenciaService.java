package com.lktransportes.service;

import com.lktransportes.model.TelemetriaViagem;
import com.lktransportes.model.Viagem;
import com.lktransportes.repository.TelemetriaViagemRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Confere, no fim da viagem, se o que o jogo reportou bate com o que foi
 * declarado. Divergiu, a viagem é RETIDA: continua no histórico, mas não pontua
 * nem entra em pagamento até um gestor liberar.
 *
 * A regra vale só para os fatos FÍSICOS. O valor do frete não é conferido aqui
 * porque não há o que conferir: ele vem da tarifa da demanda, não de um campo
 * que o motorista preenche.
 */
@Service
public class ConferenciaService {

    /** Diferença de peso aceitável entre o declarado e o que o jogo reportou. */
    private static final double TOLERANCIA_PESO = 0.05;   // 5%
    /** Abaixo disso a telemetria não viu viagem nenhuma acontecer. */
    private static final double DISTANCIA_MINIMA_KM = 1.0;

    private final TelemetriaViagemRepository telemetriaViagens;
    private final MapaService mapa;

    public ConferenciaService(TelemetriaViagemRepository telemetriaViagens, MapaService mapa) {
        this.telemetriaViagens = telemetriaViagens;
        this.mapa = mapa;
    }

    /** Roda a conferência e carimba o resultado na viagem. */
    public void conferir(Viagem v) {
        List<String> motivos = new ArrayList<>();

        // Viagem avulsa tem o frete digitado pelo motorista — não existe tarifa de
        // demanda para conferir contra. O gestor decide se aquele valor vale.
        if (v.getDemanda() == null && v.getValorFrete() != null
                && v.getValorFrete().signum() > 0) {
            motivos.add("Viagem avulsa: o valor do frete foi digitado, não veio de uma demanda.");
        }
        Optional<TelemetriaViagem> talvez = telemetriaViagens.findByViagemId(v.getId());

        if (talvez.isEmpty()) {
            // Sem isso, desligar o agente seria a maneira mais fácil de burlar tudo.
            motivos.add("A viagem não teve telemetria: não há como confirmar que ela aconteceu no jogo.");
        } else {
            TelemetriaViagem t = talvez.get();
            conferirDistancia(t, motivos);
            conferirCarga(v, t, motivos);
            conferirPeso(v, t, motivos);
            if (t.getDivergencias() != null && !t.getDivergencias().isBlank()) {
                motivos.add("Rota divergente: " + t.getDivergencias() + ".");
            }
            mapa.conferir(t, motivos);
            if (t.getSaltos() > 0) {
                motivos.add("Foram detectados %d salto(s) de posição — teleporte ou reboque.".formatted(t.getSaltos()));
            }
        }

        if (motivos.isEmpty()) {
            v.setConferencia(Viagem.Conferencia.APROVADA);
            v.setMotivosConferencia(null);
        } else {
            v.setConferencia(Viagem.Conferencia.RETIDA);
            v.setMotivosConferencia(limitar(String.join("\n", motivos)));
        }
    }

    private void conferirDistancia(TelemetriaViagem t, List<String> motivos) {
        Double km = t.getDistanciaConfirmadaKm();
        if (km == null || km < DISTANCIA_MINIMA_KM) {
            motivos.add("O jogo não confirmou distância percorrida nesta viagem.");
        }
    }

    private void conferirCarga(Viagem v, TelemetriaViagem t, List<String> motivos) {
        String doJogo = t.getCargaJogo();
        if (doJogo == null || doJogo.isBlank() || v.getCarga() == null) return;
        if (!parecido(v.getCarga(), doJogo)) {
            motivos.add("Carga declarada \"%s\" e o jogo reportou \"%s\".".formatted(v.getCarga(), doJogo));
        }
    }

    private void conferirPeso(Viagem v, TelemetriaViagem t, List<String> motivos) {
        Double doJogo = t.getPesoJogoKg();
        if (doJogo == null || doJogo <= 0 || v.getPesoKg() == null) return;

        double declarado = v.getPesoKg().doubleValue();
        if (declarado <= 0) return;

        double diferenca = Math.abs(declarado - doJogo) / declarado;
        if (diferenca > TOLERANCIA_PESO) {
            motivos.add("Peso declarado %s kg e o jogo reportou %s kg."
                    .formatted(BigDecimal.valueOf(declarado).stripTrailingZeros().toPlainString(),
                               BigDecimal.valueOf(Math.round(doJogo)).toPlainString()));
        }
    }

    /** Compara ignorando acento, caixa e pontuação — "Soja a granel" x "soja granel". */
    private boolean parecido(String a, String b) {
        String x = normalizar(a);
        String y = normalizar(b);
        return x.contains(y) || y.contains(x);
    }

    private String normalizar(String s) {
        // Sem regex de propósito: percorre os caracteres e descarta os acentos
        // (marcas de combinação) e tudo que não for letra ou dígito.
        String decomposto = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder limpo = new StringBuilder(decomposto.length());
        for (char c : decomposto.toCharArray()) {
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (Character.isLetterOrDigit(c)) limpo.append(Character.toLowerCase(c));
        }
        return limpo.toString();
    }

    private String limitar(String s) {
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
