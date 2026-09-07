package com.lktransportes.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lktransportes.model.Perfil;
import com.lktransportes.model.Usuario;
import com.lktransportes.repository.PerfilRepository;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuthService {

    @Value("${lk.discord.client-id:}")
    private String discordClientId;

    @Value("${lk.discord.client-secret:}")
    private String discordClientSecret;

    @Value("${lk.discord.redirect-uri:}")
    private String discordRedirectUri;

    @Value("${lk.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${lk.url-api:http://localhost:8080/api}")
    private String urlApi;

    private final UsuarioRepository usuarios;
    private final PerfilRepository perfis;
    private final JwtService jwt;
    private final RestClient http = RestClient.create();

    /** Tokens de pendência para vinculação Steam: code → (userId, expiry) */
    private final ConcurrentHashMap<String, SteamPending> steamPending = new ConcurrentHashMap<>();

    public OAuthService(UsuarioRepository usuarios, PerfilRepository perfis, JwtService jwt) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.jwt = jwt;
    }

    // ── Discord ──────────────────────────────────────────────────────────────

    public boolean discordConfigurado() {
        return !discordClientId.isBlank() && !discordClientSecret.isBlank();
    }

    public String urlOauthDiscord() {
        return "https://discord.com/api/oauth2/authorize" +
               "?client_id=" + discordClientId +
               "&redirect_uri=" + enc(discordRedirectUri) +
               "&response_type=code" +
               "&scope=identify+email";
    }

    @Transactional
    public ResultadoDiscord processarCallbackDiscord(String code) {
        // 1. Troca código por access token
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", discordClientId);
        form.add("client_secret", discordClientSecret);
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", discordRedirectUri);

        DiscordToken tokenResp;
        try {
            tokenResp = http.post()
                    .uri("https://discord.com/api/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(DiscordToken.class);
        } catch (Exception e) {
            return ResultadoDiscord.erro("Não foi possível conectar ao Discord. Tente de novo.");
        }

        if (tokenResp == null || tokenResp.accessToken() == null) {
            return ResultadoDiscord.erro("Código Discord inválido ou expirado.");
        }

        // 2. Busca dados do usuário
        DiscordUser du;
        try {
            du = http.get()
                    .uri("https://discord.com/api/users/@me")
                    .header("Authorization", "Bearer " + tokenResp.accessToken())
                    .retrieve()
                    .body(DiscordUser.class);
        } catch (Exception e) {
            return ResultadoDiscord.erro("Não foi possível obter dados do Discord.");
        }

        if (du == null || du.id() == null) {
            return ResultadoDiscord.erro("Resposta inválida do Discord.");
        }

        // 3. Encontra ou cria usuário
        Optional<Usuario> porDiscord = usuarios.findByDiscordId(du.id());
        if (porDiscord.isPresent()) {
            return gerarJwt(porDiscord.get());
        }

        // Tenta vincular por e-mail se o Discord fornecer um verificado
        String email = (du.email() != null && !du.email().isBlank() && Boolean.TRUE.equals(du.verified))
                ? du.email()
                : "discord-" + du.id() + "@lk.interno";

        Optional<Usuario> porEmail = usuarios.findByEmail(email);
        if (porEmail.isPresent()) {
            Usuario u = porEmail.get();
            u.setDiscordId(du.id());
            usuarios.save(u);
            return gerarJwt(u);
        }

        // Novo usuário — cria como PENDENTE, aguarda aprovação do gestor
        Usuario novo = new Usuario();
        novo.setNome(du.displayName());
        novo.setEmail(email);
        novo.setSenhaHash("{discord}"); // sentinel: não pode logar com senha
        novo.setDiscordId(du.id());
        novo.setStatusAcesso(Usuario.StatusAcesso.PENDENTE);
        usuarios.save(novo);

        Perfil p = new Perfil();
        p.setUsuario(novo);
        p.setDiscord(du.username());
        perfis.save(p);

        return ResultadoDiscord.pendente(du.displayName());
    }

    private ResultadoDiscord gerarJwt(Usuario u) {
        return switch (u.getStatusAcesso()) {
            case BLOQUEADO -> ResultadoDiscord.erro("Sua conta está bloqueada. Fale com a gestão.");
            case PENDENTE  -> ResultadoDiscord.pendente(u.getNome());
            case APROVADO  -> ResultadoDiscord.ok(
                    jwt.gerarToken(u.getId(), u.getEmail(), u.getPapel().name()),
                    u.getNome(), u.getPapel().name());
        };
    }

    /** URL de redirecionamento pós-Discord para o frontend. */
    public String urlFrontendDiscord(ResultadoDiscord r) {
        return frontendUrl + "/auth/discord?" + switch (r) {
            case ResultadoDiscord.Ok ok ->
                "token=" + ok.token() + "&nome=" + enc(ok.nome()) + "&papel=" + ok.papel();
            case ResultadoDiscord.Pendente p ->
                "pendente=1&nome=" + enc(p.nome());
            case ResultadoDiscord.Erro e ->
                "erro=" + enc(e.mensagem());
        };
    }

    // ── Steam OpenID ─────────────────────────────────────────────────────────

    /** Gera um código de uso único para iniciar a vinculação Steam. */
    public String gerarSteamCode(UUID userId) {
        limparExpirados();
        String code = UUID.randomUUID().toString().replace("-", "");
        steamPending.put(code, new SteamPending(userId, Instant.now().plusSeconds(300)));
        return code;
    }

    /** URL de autorização Steam OpenID 2.0. */
    public String urlSteamOpenId(String code) {
        // realm: domínio base da API (sem /api)
        String realm = urlApi.replaceFirst("/api(/.*)?$", "");
        String returnTo = urlApi + "/auth/steam/callback?state=" + code;
        String ns = "http://specs.openid.net/auth/2.0";
        String select = ns + "/identifier_select";
        return "https://steamcommunity.com/openid/login" +
               "?openid.ns=" + enc(ns) +
               "&openid.mode=checkid_setup" +
               "&openid.return_to=" + enc(returnTo) +
               "&openid.realm=" + enc(realm) +
               "&openid.identity=" + enc(select) +
               "&openid.claimed_id=" + enc(select);
    }

    @Transactional
    public Optional<String> processarCallbackSteam(String state, Map<String, String> params) {
        SteamPending pending = steamPending.remove(state);
        if (pending == null || Instant.now().isAfter(pending.expira())) return Optional.empty();
        if (!"id_res".equals(params.get("openid.mode"))) return Optional.empty();
        if (!verificarSteamOpenId(params)) return Optional.empty();

        String claimedId = params.getOrDefault("openid.claimed_id", "");
        String prefixo = "https://steamcommunity.com/openid/id/";
        if (!claimedId.startsWith(prefixo)) return Optional.empty();
        String steamId = claimedId.substring(prefixo.length());
        if (!steamId.matches("\\d{17}")) return Optional.empty();

        Perfil perfil = perfis.findByUsuarioId(pending.userId()).orElseGet(() -> {
            Perfil novo = new Perfil();
            novo.setUsuario(usuarios.findById(pending.userId()).orElseThrow());
            return novo;
        });
        perfil.setSteamId(steamId);
        perfis.save(perfil);
        return Optional.of(steamId);
    }

    private boolean verificarSteamOpenId(Map<String, String> params) {
        // Reenvia para Steam com mode=check_authentication; Steam confirma is_valid:true
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        form.set("openid.mode", "check_authentication");
        try {
            String resp = http.post()
                    .uri("https://steamcommunity.com/openid/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return resp != null && resp.contains("is_valid:true");
        } catch (Exception e) {
            return false;
        }
    }

    public String urlFrontendSteam(Optional<String> steamId) {
        return frontendUrl + "/auth/steam?" +
               (steamId.isPresent()
                   ? "ok=1&steamId=" + steamId.get()
                   : "erro=" + enc("Não foi possível validar a conta Steam."));
    }

    // ── Utilitários ──────────────────────────────────────────────────────────

    private void limparExpirados() {
        Instant agora = Instant.now();
        steamPending.entrySet().removeIf(e -> agora.isAfter(e.getValue().expira()));
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── Records internos ─────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DiscordToken(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DiscordUser(
            String id,
            String username,
            @JsonProperty("global_name") String globalName,
            String email,
            Boolean verified
    ) {
        String displayName() {
            return globalName != null && !globalName.isBlank() ? globalName : username;
        }
    }

    record SteamPending(UUID userId, Instant expira) {}

    // ── Resultado do fluxo Discord ────────────────────────────────────────────

    public sealed interface ResultadoDiscord
            permits ResultadoDiscord.Ok, ResultadoDiscord.Pendente, ResultadoDiscord.Erro {

        record Ok(String token, String nome, String papel) implements ResultadoDiscord {}
        record Pendente(String nome) implements ResultadoDiscord {}
        record Erro(String mensagem) implements ResultadoDiscord {}

        static ResultadoDiscord ok(String t, String n, String p) { return new Ok(t, n, p); }
        static ResultadoDiscord pendente(String n) { return new Pendente(n); }
        static ResultadoDiscord erro(String m) { return new Erro(m); }
    }
}
