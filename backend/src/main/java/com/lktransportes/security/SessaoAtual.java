package com.lktransportes.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Quem está falando com a API agora, segundo o token. */
@Component
public class SessaoAtual {

    public Optional<JwtService.Identidade> identidade() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtService.Identidade id)) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    public JwtService.Identidade obrigatoria() {
        return identidade().orElseThrow(AcessoNegadoException::new);
    }

    public UUID id() {
        return obrigatoria().id();
    }

    public boolean eGestor() {
        return identidade().map(i -> "GESTOR".equals(i.papel())).orElse(false);
    }

    /**
     * Porta de entrada de tudo que é pessoal: só o próprio dono ou um gestor passa.
     * Sem isso, trocar o id na URL daria acesso aos dados de qualquer motorista.
     */
    public void exigirDonoOuGestor(UUID donoId) {
        var eu = obrigatoria();
        if (!eu.id().equals(donoId) && !"GESTOR".equals(eu.papel())) {
            throw new AcessoNegadoException();
        }
    }

    public void exigirGestor() {
        if (!eGestor()) throw new AcessoNegadoException();
    }

    public static class AcessoNegadoException extends RuntimeException {
        public AcessoNegadoException() { super("Você não tem acesso a este recurso."); }
    }

    /**
     * Token válido, usuário inexistente. Vira 401 (e não 403 nem 404) porque o
     * caminho certo é o painel derrubar a sessão e pedir login de novo.
     */
    public static class SessaoInvalidaException extends RuntimeException {
        public SessaoInvalidaException(String mensagem) { super(mensagem); }
    }
}
