package com.lktransportes.service;

import com.lktransportes.model.*;
import com.lktransportes.repository.CnhRepository;
import com.lktransportes.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Emissão, pontuação e bloqueio da CNH.
 *
 * A pontuação é o que amarra comportamento a consequência: cada multa e cada
 * avaria detectada pela telemetria descontam, e chegar a zero tira o motorista
 * de circulação até a gestão reabilitar.
 */
@Service
public class CnhService {

    /** Quanto cada coisa custa da carteira. */
    public static final int PONTOS_POR_MULTA = 5;
    public static final int PONTOS_POR_AVARIA = 3;
    /** Prazo padrão de uma emissão ou renovação. */
    public static final int MESES_DE_VALIDADE = 3;

    private final CnhRepository cnhs;
    private final UsuarioRepository usuarios;

    public CnhService(CnhRepository cnhs, UsuarioRepository usuarios) {
        this.cnhs = cnhs;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public Optional<Cnh> de(UUID motoristaId) {
        return cnhs.findByMotoristaId(motoristaId);
    }

    @Transactional
    public Cnh emitir(UUID motoristaId, String categoria, LocalDate validade, Usuario gestor) {
        Usuario m = usuarios.findById(motoristaId).orElseThrow();
        Cnh c = cnhs.findByMotoristaId(motoristaId).orElseGet(() -> {
            Cnh nova = new Cnh();
            nova.setMotorista(m);
            nova.setNumeroRegistro(gerarNumero());
            nova.setPrimeiraHabilitacao(LocalDate.now());
            return nova;
        });

        if (categoria != null && !categoria.isBlank()) c.setCategoria(categoria);
        c.renovar(validade != null ? validade : LocalDate.now().plusMonths(MESES_DE_VALIDADE));
        c.setEmitidaEm(java.time.LocalDateTime.now());
        c.setEmitidaPor(gestor);
        return cnhs.save(c);
    }

    /** Volta a valer sem mexer no prazo — usado quando a suspensão foi por pontos. */
    @Transactional
    public Cnh reabilitar(UUID motoristaId, Usuario gestor, String observacao) {
        Cnh c = cnhs.findByMotoristaId(motoristaId).orElseThrow();
        c.setPontos(Cnh.PONTOS_INICIAIS);
        c.setSituacao(Cnh.Situacao.ATIVA);
        c.setObservacoes(observacao);
        c.setEmitidaPor(gestor);
        return cnhs.save(c);
    }

    @Transactional
    public Cnh suspender(UUID motoristaId, Usuario gestor, String observacao) {
        Cnh c = cnhs.findByMotoristaId(motoristaId).orElseThrow();
        c.setSituacao(Cnh.Situacao.SUSPENSA);
        c.setObservacoes(observacao);
        c.setEmitidaPor(gestor);
        return cnhs.save(c);
    }

    /**
     * Passa a viagem concluída na carteira: cada multa e cada avaria detectada
     * pela telemetria descontam pontos.
     *
     * @return quantos pontos foram perdidos nesta viagem.
     */
    @Transactional
    public int cobrarDaViagem(Viagem v) {
        Optional<Cnh> talvez = cnhs.findByMotoristaId(v.getMotorista().getId());
        if (talvez.isEmpty()) return 0;

        int multas = 0;
        int avarias = 0;
        for (EventoViagem e : v.getEventos()) {
            if (e instanceof Multa) {
                multas++;
            } else if (e instanceof Ocorrencia && e.getOrigem() == EventoViagem.Origem.TELEMETRIA) {
                avarias++;
            }
        }
        int total = multas * PONTOS_POR_MULTA + avarias * PONTOS_POR_AVARIA;
        if (total == 0) return 0;

        Cnh c = talvez.get();
        int perdidos = c.descontar(total);
        cnhs.save(c);
        return perdidos;
    }

    /**
     * Porta de entrada do trabalho: sem CNH válida o motorista não pega carga.
     * É esse bloqueio que faz a renovação importar.
     */
    @Transactional(readOnly = true)
    public void exigirCnhValida(UUID motoristaId) {
        Cnh c = cnhs.findByMotoristaId(motoristaId).orElseThrow(() -> new IllegalStateException(
                "Você ainda não tem CNH emitida. Peça a emissão à gestão."));
        if (!c.valida()) {
            throw new IllegalStateException(c.motivoDoBloqueio() + " Procure a gestão para regularizar.");
        }
    }

    /** Número de registro fictício, no formato de 11 dígitos da CNH. */
    private String gerarNumero() {
        long n = Math.abs(java.util.UUID.randomUUID().getLeastSignificantBits() % 100_000_000_000L);
        return String.format("%011d", n);
    }
}
