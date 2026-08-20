package com.lktransportes.controller;

import com.lktransportes.dto.AbastecimentoRequest;
import com.lktransportes.dto.EventoResponse;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/viagens/{viagemId}/abastecimentos")
public class AbastecimentoController {

    private final ViagemRepository viagens;
    private final PostoRepository postos;
    private final EventoViagemRepository eventos;

    private final com.lktransportes.security.SessaoAtual sessao;

    public AbastecimentoController(ViagemRepository viagens, PostoRepository postos,
                                   EventoViagemRepository eventos,
                                   com.lktransportes.security.SessaoAtual sessao) {
        this.viagens = viagens;
        this.postos = postos;
        this.eventos = eventos;
        this.sessao = sessao;
    }

    @PostMapping
    @Transactional
    public EventoResponse registrar(@PathVariable UUID viagemId, @RequestBody AbastecimentoRequest req) {
        Viagem viagem = viagens.findById(viagemId).orElseThrow();
        sessao.exigirDonoOuGestor(viagem.getMotorista().getId());
        if (viagem.getStatus() != StatusViagem.EM_ANDAMENTO) {
            throw new IllegalStateException("Só é possível registrar eventos em viagem em andamento.");
        }
        if (req.assinaturaBase64 == null || req.assinaturaBase64.isBlank()) {
            throw new IllegalArgumentException("A assinatura do motorista é obrigatória no abastecimento.");
        }

        Abastecimento a = new Abastecimento();
        a.setViagem(viagem);
        a.setPosto(postos.findById(req.postoId).orElseThrow());
        a.setLitros(req.litros);
        a.setValorLitro(req.valorLitro);
        a.calcularTotal();                        // total = litros x preço, nunca digitado
        a.setAssinaturaBase64(req.assinaturaBase64);
        if (req.ocorridoEm != null) a.setOcorridoEm(req.ocorridoEm);
        a.setObservacao(req.observacao);

        return EventoResponse.de(eventos.save(a));
    }
}
