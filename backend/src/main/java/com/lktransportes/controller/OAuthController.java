package com.lktransportes.controller;

import com.lktransportes.security.SessaoAtual;
import com.lktransportes.service.OAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints públicos para OAuth com Discord e vinculação de conta Steam.
 *
 * Discord: fluxo completo de login (redirect → callback → JWT).
 * Steam:   só vinculação (usuário já logado via JWT gera um código,
 *          é redirecionado ao Steam, Steam valida e salva o Steam ID no perfil).
 */
@RestController
@RequestMapping("/api/auth")
public class OAuthController {

    private final OAuthService oauth;
    private final SessaoAtual sessao;

    public OAuthController(OAuthService oauth, SessaoAtual sessao) {
        this.oauth = oauth;
        this.sessao = sessao;
    }

    // ── Discord ──────────────────────────────────────────────────────────────

    /** Redireciona para a tela de autorização do Discord. */
    @GetMapping("/discord")
    public void iniciarDiscord(HttpServletResponse resp) throws IOException {
        if (!oauth.discordConfigurado()) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                    "Login com Discord não configurado. Defina DISCORD_CLIENT_ID e DISCORD_CLIENT_SECRET.");
            return;
        }
        resp.sendRedirect(oauth.urlOauthDiscord());
    }

    /** Callback que o Discord chama após o usuário autorizar. */
    @GetMapping("/discord/callback")
    public void callbackDiscord(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String error,
                                HttpServletResponse resp) throws IOException {
        if (error != null || code == null) {
            resp.sendRedirect(oauth.urlFrontendDiscord(
                    OAuthService.ResultadoDiscord.erro("Autorização cancelada.")));
            return;
        }
        OAuthService.ResultadoDiscord resultado = oauth.processarCallbackDiscord(code);
        resp.sendRedirect(oauth.urlFrontendDiscord(resultado));
    }

    /** Retorna se o Discord está configurado — o frontend usa para mostrar/ocultar o botão. */
    @GetMapping("/info")
    public Map<String, Boolean> info() {
        return Map.of("discord", oauth.discordConfigurado());
    }

    // ── Steam ─────────────────────────────────────────────────────────────────

    /**
     * Gera um código de uso único para iniciar a vinculação Steam.
     * Exige JWT (o usuário já está logado).
     */
    @PostMapping("/steam/gerar-token")
    public Map<String, String> gerarTokenSteam() {
        String code = oauth.gerarSteamCode(sessao.id());
        return Map.of("code", code);
    }

    /** Redireciona para o login Steam com o código gerado. */
    @GetMapping("/steam/iniciar")
    public void iniciarSteam(@RequestParam String code, HttpServletResponse resp) throws IOException {
        resp.sendRedirect(oauth.urlSteamOpenId(code));
    }

    /** Callback que o Steam chama após o usuário autenticar. */
    @GetMapping("/steam/callback")
    public void callbackSteam(@RequestParam(value = "state", required = false) String state,
                              @RequestParam Map<String, String> params,
                              HttpServletResponse resp) throws IOException {
        if (state == null) {
            resp.sendRedirect(oauth.urlFrontendSteam(Optional.empty()));
            return;
        }
        Optional<String> steamId = oauth.processarCallbackSteam(state, params);
        resp.sendRedirect(oauth.urlFrontendSteam(steamId));
    }
}
