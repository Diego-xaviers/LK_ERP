package com.lktransportes.seguranca;

import com.lktransportes.model.Usuario;
import com.lktransportes.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * A regra de privacidade do sistema, testada por HTTP de verdade.
 *
 * Aqui não dá para testar só o serviço: quem decide isto é a combinação do
 * FiltroJwt, do SecurityConfig e do SessaoAtual dentro de cada controller. Um
 * matcher afrouxado no SecurityConfig não aparece em teste de serviço nenhum —
 * aparece aqui, ou não aparece até alguém ler os dados de outro motorista.
 *
 * A regra é: cada motorista vê os próprios dados e nada dos outros.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("dev")
class AutorizacaoTest {

    /**
     * Cliente do JDK em vez de TestRestTemplate: o HttpURLConnection por trás do
     * RestTemplate tenta reautenticar sozinho quando leva 401 com corpo, e
     * estoura antes de devolver o status que este teste quer justamente medir.
     */
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();

    @LocalServerPort int porta;
    @Autowired UsuarioRepository usuarios;

    private String tokenMotorista;
    private String tokenGestor;
    private UUID idMotorista;
    private UUID idGestor;

    @BeforeEach
    void entrar() {
        Usuario m = usuarios.findByEmail("motorista@lk.com").orElseThrow();
        Usuario g = usuarios.findByEmail("admin@lk.com").orElseThrow();
        idMotorista = m.getId();
        idGestor = g.getId();
        tokenMotorista = login("motorista@lk.com");
        tokenGestor = login("admin@lk.com");
    }

    // ----- o que o motorista NÃO pode -----

    @Test
    @DisplayName("motorista não lê dados de outro motorista")
    void motoristaNaoLeDadosDeOutro() {
        // O gestor serve de "outro" aqui: qualquer id que não seja o dele.
        assertThat(status("/api/perfil/" + idGestor, tokenMotorista)).isEqualTo(403);
        assertThat(status("/api/cnh/" + idGestor, tokenMotorista)).isEqualTo(403);
        assertThat(status("/api/financeiro/meus-ganhos/" + idGestor, tokenMotorista)).isEqualTo(403);
        assertThat(status("/api/viagens/motorista/" + idGestor, tokenMotorista)).isEqualTo(403);
        assertThat(status("/api/telemetria/atual/" + idGestor, tokenMotorista)).isEqualTo(403);
    }

    @Test
    @DisplayName("motorista não entra no que é do gestor")
    void motoristaNaoEntraNoQueEDoGestor() {
        assertThat(status("/api/usuarios", tokenMotorista)).isEqualTo(403);
        assertThat(status("/api/demandas", tokenMotorista)).isEqualTo(403);
        assertThat(status("/api/viagens/retidas", tokenMotorista)).isEqualTo(403);
        assertThat(status("/api/financeiro/painel", tokenMotorista)).isEqualTo(403);
    }

    @Test
    @DisplayName("motorista não cria nem altera cadastro da empresa")
    void motoristaNaoAlteraCadastros() {
        assertThat(post("/api/caminhoes", tokenMotorista,
                """
                {"placa":"XXX-0000","modelo":"R450"}""")).isEqualTo(403);
        assertThat(post("/api/loja/itens", tokenMotorista,
                """
                {"nome":"brinde","preco":1}""")).isEqualTo(403);
        assertThat(post("/api/financeiro/valor-km-padrao", tokenMotorista,
                """
                {"valorKm":"99"}""")).isEqualTo(403);
        assertThat(post("/api/financeiro/ajuste", tokenMotorista,
                """
                {"valor":1000,"descricao":"meu"}""")).isEqualTo(403);
    }

    @Test
    @DisplayName("motorista não muda o próprio papel para gestor")
    void motoristaNaoSePromove() {
        try {
            int status = enviar("PUT", "/api/usuarios/" + idMotorista, tokenMotorista,
                    """
                    {"nome":"Stilnoxgg","email":"motorista@lk.com","papel":"GESTOR"}""");

            assertThat(status).isEqualTo(403);
            assertThat(usuarios.findById(idMotorista).orElseThrow().getPapel())
                    .describedAs("o papel não pode ter mudado")
                    .isEqualTo(Usuario.Papel.MOTORISTA);
        } finally {
            // Se esta amarra cair, o motorista vira gestor NO BANCO — e como os
            // testes compartilham o mesmo banco, todos os seguintes passariam a
            // rodar com um "motorista" graduado, escondendo a falha real numa
            // cascata de erros. Desfazer aqui mantém a falha onde ela nasceu.
            Usuario m = usuarios.findById(idMotorista).orElseThrow();
            if (m.getPapel() != Usuario.Papel.MOTORISTA) {
                m.setPapel(Usuario.Papel.MOTORISTA);
                usuarios.save(m);
            }
        }
    }

    // ----- o que o motorista PODE -----

    @Test
    @DisplayName("motorista lê os próprios dados")
    void motoristaLeOsProprios() {
        assertThat(status("/api/usuarios/atual", tokenMotorista)).isEqualTo(200);
        assertThat(status("/api/perfil/" + idMotorista, tokenMotorista)).isEqualTo(200);
        assertThat(status("/api/cnh/" + idMotorista, tokenMotorista)).isEqualTo(200);
        assertThat(status("/api/financeiro/meus-ganhos/" + idMotorista, tokenMotorista)).isEqualTo(200);
        assertThat(status("/api/viagens/motorista/" + idMotorista, tokenMotorista)).isEqualTo(200);
        assertThat(status("/api/demandas/abertas", tokenMotorista)).isEqualTo(200);
        assertThat(status("/api/viagens/empresa", tokenMotorista))
                .describedAs("o mural da empresa é a exceção combinada à regra de privacidade")
                .isEqualTo(200);
    }

    // ----- o agente de telemetria -----

    @Test
    @DisplayName("nem o gestor baixa o agente de outro: o pacote carrega o token do motorista")
    void gestorNaoBaixaAgenteAlheio() {
        assertThat(status("/api/telemetria/agente/" + idMotorista, tokenGestor))
                .describedAs("baixar o pacote é o mesmo que pegar a credencial dele")
                .isEqualTo(403);
        assertThat(status("/api/telemetria/agente/" + idMotorista, tokenMotorista))
                .describedAs("o dono baixa o próprio")
                .isEqualTo(200);
    }

    // ----- sem token, ou com token inválido -----

    @Test
    @DisplayName("sem token é 401, não 403 — é o 401 que devolve o painel ao login")
    void semTokenEh401() {
        assertThat(status("/api/viagens", null)).isEqualTo(401);
        assertThat(status("/api/usuarios/atual", null)).isEqualTo(401);
        assertThat(status("/api/viagens", "token.invalido.aaa")).isEqualTo(401);
    }

    @Test
    @DisplayName("as portas abertas continuam abertas — e só elas")
    void portasAbertas() {
        assertThat(status("/actuator/health", null)).isEqualTo(200);
        assertThat(post("/api/auth/login", null,
                """
                {"email":"motorista@lk.com","senha":"123456"}""")).isEqualTo(200);
        // O /telemetria/ping é aberto de propósito: o agente se identifica pelo
        // token dele, não por JWT. Sem esse cabeçalho, recusa — mas recusa no
        // TelemetriaService, não no SecurityConfig.
        assertThat(post("/api/telemetria/ping", null,
                """
                {"jogoLigado":true}""")).isEqualTo(401);
    }

    @Test
    @DisplayName("o gestor enxerga o time, como combinado")
    void gestorEnxergaOTime() {
        assertThat(status("/api/usuarios", tokenGestor)).isEqualTo(200);
        assertThat(status("/api/financeiro/painel", tokenGestor)).isEqualTo(200);
        assertThat(status("/api/viagens/retidas", tokenGestor)).isEqualTo(200);
        assertThat(status("/api/perfil/" + idMotorista, tokenGestor)).isEqualTo(200);
        assertThat(status("/api/cnh/" + idMotorista, tokenGestor)).isEqualTo(200);
    }

    // ----- apoio -----

    private String login(String email) {
        HttpResponse<String> r = responder("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"senha\":\"123456\"}");
        // Sem parser de JSON aqui de propósito: o teste é de status, e uma
        // dependência a mais só para ler um campo não se paga.
        return r.body().replaceAll(".*\"token\"\s*:\s*\"([^\"]+)\".*", "$1");
    }

    private int status(String caminho, String token) {
        return enviar("GET", caminho, token, null);
    }

    private int post(String caminho, String token, String corpoJson) {
        return enviar("POST", caminho, token, corpoJson);
    }

    private int enviar(String metodo, String caminho, String token, String corpoJson) {
        return responder(metodo, caminho, token, corpoJson).statusCode();
    }

    private HttpResponse<String> responder(String metodo, String caminho, String token, String corpoJson) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpoJson == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(corpoJson));
        if (token != null) b.header("Authorization", "Bearer " + token);
        try {
            return CLIENTE.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao chamar " + metodo + " " + caminho, e);
        }
    }
}
