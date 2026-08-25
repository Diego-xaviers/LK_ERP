package com.lktransportes.config;

import com.lktransportes.security.FiltroJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final FiltroJwt filtroJwt;

    /**
     * Origens que podem chamar a API. Em produção é o domínio do painel —
     * separar por vírgula para mais de um.
     */
    @Value("${lk.origens-permitidas:http://localhost:5173,http://127.0.0.1:5173}")
    private String origensPermitidas;

    /** O console do H2 só existe em desenvolvimento; em produção fica fora. */
    @Value("${lk.h2-console-liberado:false}")
    private boolean h2ConsoleLiberado;

    public SecurityConfig(FiltroJwt filtroJwt) {
        this.filtroJwt = filtroJwt;
    }

    /**
     * Regra geral: tudo exige token. As exceções são poucas e explícitas.
     *
     * O /telemetria/ping fica aberto de propósito — quem chama é o agente que
     * roda na máquina do motorista, e ele se identifica pelo X-Telemetria-Token
     * (validado no TelemetriaService), não por JWT.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/telemetria/ping").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    // O /error é para onde o Spring encaminha 404 e corpo malformado.
                    // Sem liberar, esse encaminhamento chega aqui sem o header
                    // Authorization e vira um 403 vazio — o front mostrava "erro
                    // inesperado" para o que era só uma rota errada.
                    .requestMatchers("/error").permitAll();

                if (h2ConsoleLiberado) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }

                // Antes da regra geral de /usuarios: todo logado precisa saber quem é.
                auth.requestMatchers("/api/usuarios/atual").authenticated()
                    // Listar e mexer em usuários é só do gestor — é aqui que se fechava
                    // a porta de enumerar motoristas para pegar os ids alheios.
                    .requestMatchers("/api/usuarios/**").hasRole("GESTOR")

                    // Cadastros da transportadora: motorista lê, só gestor altera.
                    .requestMatchers(HttpMethod.POST, "/api/postos/**", "/api/oficinas/**",
                            "/api/carretas/**", "/api/empresas/**", "/api/avisos/**",
                            "/api/caminhoes/**").hasRole("GESTOR")
                    .requestMatchers(HttpMethod.DELETE, "/api/postos/**", "/api/oficinas/**",
                            "/api/carretas/**", "/api/empresas/**", "/api/avisos/**",
                            "/api/caminhoes/**").hasRole("GESTOR")

                    // Toda a API exige token. O que sobra é o painel — index,
                    // assets e as rotas que só existem dentro do React — servido
                    // pelo próprio backend no modo demo. Página é pública por
                    // definição: quem protege os dados é o /api logo acima, e é
                    // no login que o painel vira alguma coisa.
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll();
            })
            // Sem token, ou com token vencido, a resposta é 401 — não 403.
            // O painel derruba a sessão e volta pro login em cima do 401; com o
            // 403 padrão do Spring o motorista ficava vendo erro em toda tela
            // depois que o token expirava, sem ser mandado de volta pro login.
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, resp, e) -> {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().write("{\"erro\":\"Sessão expirada. Entre de novo.\"}");
            }))
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> {
                if (h2ConsoleLiberado) headers.frameOptions(frame -> frame.disable());
            });
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origensPermitidas.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
