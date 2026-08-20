package com.lktransportes.controller;

import com.lktransportes.dto.TelemetriaPing;
import com.lktransportes.model.TelemetriaSessao;
import com.lktransportes.model.TelemetriaViagem;
import com.lktransportes.model.Usuario;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.service.TelemetriaService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/telemetria")
public class TelemetriaController {

    private final TelemetriaService service;
    private final UsuarioRepository usuarios;
    private final com.lktransportes.security.SessaoAtual sessao;

    /**
     * URL que o agente vai chamar. Ele roda fora do navegador, então precisa da
     * absoluta — em produção, apontar para o endereço público da API.
     */
    @org.springframework.beans.factory.annotation.Value("${lk.url-api:http://localhost:8080/api}")
    private String urlDaApi;

    public TelemetriaController(TelemetriaService service, UsuarioRepository usuarios,
                                com.lktransportes.security.SessaoAtual sessao) {
        this.service = service;
        this.usuarios = usuarios;
        this.sessao = sessao;
    }

    // ---------------- Ingestão (chamada pelo agente) ----------------

    @PostMapping("/ping")
    public Map<String, Object> ping(@RequestHeader(value = "X-Telemetria-Token", required = false) String token,
                                    @RequestBody TelemetriaPing corpo) {
        Usuario motorista = service.autenticar(token);
        service.registrar(motorista, corpo);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("motorista", motorista.getNome());
        // O agente mostra o número na janela; null = nenhuma viagem em andamento.
        resposta.put("viagem", service.numeroDaViagemAtiva(motorista.getId()));
        return resposta;
    }

    // ---------------- Consulta (chamada pelo painel) ----------------

    @GetMapping("/atual/{motoristaId}")
    public ResponseEntity<Map<String, Object>> atual(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        return service.sessaoDe(motoristaId)
                .<ResponseEntity<Map<String, Object>>>map(s -> ResponseEntity.ok(comoMapa(s)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/viagem/{viagemId}")
    public ResponseEntity<TelemetriaViagem> daViagem(@PathVariable UUID viagemId) {
        sessao.exigirDonoOuGestor(service.donoDaViagem(viagemId));
        return service.daViagem(viagemId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private Map<String, Object> comoMapa(TelemetriaSessao s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("online", s.estaOnline());
        m.put("atualizadoEm", s.getAtualizadoEm());
        m.put("velocidadeKmh", s.getVelocidadeKmh());
        m.put("rpm", s.getRpm());
        m.put("marcha", s.getMarcha());
        m.put("combustivelL", s.getCombustivelL());
        m.put("combustivelCapacidadeL", s.getCombustivelCapacidadeL());
        m.put("odometroKm", s.getOdometroKm());
        m.put("danoMotorPct", s.getDanoMotorPct());
        m.put("danoCambioPct", s.getDanoCambioPct());
        m.put("danoCabinePct", s.getDanoCabinePct());
        m.put("danoChassiPct", s.getDanoChassiPct());
        m.put("danoRodasPct", s.getDanoRodasPct());
        m.put("danoCargaPct", s.getDanoCargaPct());
        m.put("posX", s.getPosX());
        m.put("posZ", s.getPosZ());
        m.put("pilotoAutomatico", s.getPilotoAutomatico());
        m.put("pausado", s.getPausado());
        m.put("emServico", s.getEmServico());
        m.put("cargaNome", s.getCargaNome());
        m.put("cargaMassaKg", s.getCargaMassaKg());
        m.put("cidadeOrigem", s.getCidadeOrigem());
        m.put("cidadeDestino", s.getCidadeDestino());
        m.put("empresaOrigem", s.getEmpresaOrigem());
        m.put("empresaDestino", s.getEmpresaDestino());
        m.put("distanciaPlanejadaKm", s.getDistanciaPlanejadaKm());
        m.put("placaCaminhao", s.getPlacaCaminhao());
        m.put("modeloCaminhao", s.getModeloCaminhao());
        return m;
    }

    // ---------------- Pareamento e download ----------------

    /** Mostra se já existe token, sem devolver o segredo em si. */
    @GetMapping("/pareamento/{motoristaId}")
    public Map<String, Object> pareamento(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        String token = service.tokenDe(motoristaId);
        return Map.of("configurado", token != null);
    }

    @PostMapping("/pareamento/{motoristaId}")
    public Map<String, String> gerar(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        service.gerarToken(motoristaId);
        return Map.of("mensagem", "Token gerado. Baixe o agente novamente para usar o novo token.");
    }

    /**
     * Devolve o agente já com o token do motorista dentro, para ele só descompactar
     * e clicar. Regenerar o token invalida os pacotes baixados antes.
     */
    @GetMapping("/agente/{motoristaId}")
    public ResponseEntity<byte[]> baixarAgente(@PathVariable UUID motoristaId) throws IOException {
        // Só o dono, nem gestor: o pacote contém o token, e quem tem o token pode
        // enviar telemetria no nome do motorista. Gestor pode regerar, não baixar.
        if (!sessao.id().equals(motoristaId)) {
            throw new com.lktransportes.security.SessaoAtual.AcessoNegadoException();
        }
        Usuario motorista = usuarios.findById(motoristaId).orElseThrow();
        String token = motorista.getTokenTelemetria();
        if (token == null) {
            token = service.gerarToken(motoristaId);
        }

        String config = """
                {
                  "servidor": "%s",
                  "token": "%s",
                  "motorista": "%s"
                }
                """.formatted(urlDaApi, token, motorista.getNome().replace("\"", ""));

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida, StandardCharsets.UTF_8)) {
            copiar(zip, "agente/LK-Telemetria.bat", "LK-Telemetria.bat");
            copiar(zip, "agente/lk-telemetria.ps1", "lk-telemetria.ps1");
            copiar(zip, "agente/LEIA-ME.txt", "LEIA-ME.txt");
            escrever(zip, "lk-telemetria.json", config);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"LK-Telemetria.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(saida.toByteArray());
    }


    private void copiar(ZipOutputStream zip, String recurso, String nomeNoZip) throws IOException {
        byte[] conteudo = new ClassPathResource(recurso).getInputStream().readAllBytes();
        zip.putNextEntry(new ZipEntry(nomeNoZip));
        zip.write(conteudo);
        zip.closeEntry();
    }

    private void escrever(ZipOutputStream zip, String nomeNoZip, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nomeNoZip));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
