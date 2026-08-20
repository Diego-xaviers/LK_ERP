package com.lktransportes.controller;

import com.lktransportes.dto.*;
import com.lktransportes.model.*;
import com.lktransportes.repository.*;
import com.lktransportes.service.ViagemService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/viagens")
public class ViagemController {

    private final ViagemService service;
    private final ViagemRepository viagens;
    private final OficinaRepository oficinas;
    private final EventoViagemRepository eventos;

    private final com.lktransportes.security.SessaoAtual sessao;
    private final com.lktransportes.repository.UsuarioRepository usuarios;

    public ViagemController(ViagemService service, ViagemRepository viagens,
                            OficinaRepository oficinas, EventoViagemRepository eventos,
                            com.lktransportes.security.SessaoAtual sessao,
                            com.lktransportes.repository.UsuarioRepository usuarios) {
        this.service = service;
        this.viagens = viagens;
        this.oficinas = oficinas;
        this.eventos = eventos;
        this.sessao = sessao;
        this.usuarios = usuarios;
    }

    /** Viagem é dado pessoal do motorista: ou é dele, ou quem pergunta é gestor. */
    private void exigirAcessoA(UUID viagemId) {
        sessao.exigirDonoOuGestor(viagens.findById(viagemId).orElseThrow().getMotorista().getId());
    }

    // ---------- Consulta ----------

    /** Listagem completa, com eventos e documentos de todo mundo — só gestor. */
    @GetMapping
    public List<ViagemResponse> listar() {
        sessao.exigirGestor();
        return service.listarTodas();
    }

    /**
     * Mural da empresa: as viagens de todos, mas só o resumo. É o que alimenta
     * o painel e o ranking — todo motorista logado enxerga.
     */
    @GetMapping("/empresa")
    public List<com.lktransportes.dto.ViagemResumoResponse> empresa() {
        return service.feedDaEmpresa();
    }

    @GetMapping("/{id}")
    public ViagemResponse buscar(@PathVariable UUID id) {
        exigirAcessoA(id);
        return service.buscar(id);
    }

    @GetMapping("/ativa/{motoristaId}")
    public ViagemResponse ativa(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        return service.viagemAtiva(motoristaId);
    }

    @GetMapping("/motorista/{motoristaId}")
    public List<ViagemResponse> historico(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        return service.historicoDo(motoristaId);
    }

    // ---------- Ciclo de vida ----------

    @PostMapping
    public ViagemResponse criar(@Valid @RequestBody NovaViagemRequest req) {
        // Impede criar viagem no nome de outro motorista mandando outro id no corpo.
        sessao.exigirDonoOuGestor(req.motoristaId);
        return service.criarViagem(req);
    }

    @PostMapping("/{id}/documentos")
    public List<DocumentoResponse> gerarDocumentos(@PathVariable UUID id) {
        exigirAcessoA(id);
        return service.gerarDocumentos(id).stream().map(DocumentoResponse::de).toList();
    }

    @GetMapping("/{id}/documentos")
    public List<DocumentoResponse> documentos(@PathVariable UUID id) {
        exigirAcessoA(id);
        return service.documentosDa(id).stream().map(DocumentoResponse::de).toList();
    }

    @PostMapping("/{id}/iniciar")
    public ViagemResponse iniciar(@PathVariable UUID id) {
        exigirAcessoA(id);
        return service.iniciar(id);
    }

    @PostMapping("/{id}/finalizar")
    public ViagemResponse finalizar(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        exigirAcessoA(id);
        String obs = body == null ? null : (String) body.get("observacaoFinal");
        Boolean avaria = body == null ? null : (Boolean) body.get("houveAvaria");
        return service.finalizar(id, obs, avaria);
    }

    // ---------- Eventos ----------

    @PostMapping("/{id}/eventos/{tipo}")
    @Transactional
    public EventoResponse registrarEvento(@PathVariable UUID id, @PathVariable String tipo,
                                          @RequestBody EventoRequest req) {
        exigirAcessoA(id);
        Viagem viagem = viagens.findById(id).orElseThrow();
        if (viagem.getStatus() != StatusViagem.EM_ANDAMENTO) {
            throw new IllegalStateException("Só é possível registrar eventos em viagem em andamento.");
        }

        EventoViagem evento = switch (tipo.toLowerCase()) {
            case "pedagio" -> {
                exigirValor(req.valor, "Informe o valor do pedágio.");
                Pedagio p = new Pedagio();
                p.setLocal(req.local);
                p.setValor(req.valor);
                yield p;
            }
            case "multa" -> {
                exigirTexto(req.motivo, "Informe o motivo da multa.");
                exigirValor(req.valor, "Informe o valor da multa.");
                Multa m = new Multa();
                m.setMotivo(req.motivo.trim());
                m.setLocal(req.local);
                m.setEvidenciaUrl(req.evidenciaUrl);
                m.setValor(req.valor);
                yield m;
            }
            case "manutencao" -> {
                if (req.oficinaId == null) {
                    throw new IllegalArgumentException("Escolha a oficina que fez o serviço.");
                }
                exigirTexto(req.servico, "Descreva o serviço feito na oficina.");
                exigirValor(req.valor, "Informe o valor da manutenção.");
                Manutencao m = new Manutencao();
                m.setOficina(oficinas.findById(req.oficinaId).orElseThrow(
                        () -> new NoSuchElementException("Oficina não encontrada.")));
                m.setServico(req.servico.trim());
                m.setCaminhao(viagem.getCaminhao());
                m.setValor(req.valor);
                yield m;
            }
            case "ocorrencia" -> {
                exigirTexto(req.titulo, "Dê um título à ocorrência.");
                Ocorrencia o = new Ocorrencia();
                o.setTitulo(req.titulo.trim());
                o.setDescricao(req.descricao);
                o.setEvidenciaUrl(req.evidenciaUrl);
                yield o;
            }
            default -> throw new IllegalArgumentException("Tipo de evento desconhecido: " + tipo);
        };

        evento.setViagem(viagem);
        evento.setObservacao(req.observacao);
        if (req.ocorridoEm != null) evento.setOcorridoEm(req.ocorridoEm);

        return EventoResponse.de(eventos.save(evento));
    }

    /**
     * A herança dos eventos é SINGLE_TABLE: as colunas de todos os subtipos moram
     * na mesma tabela e por isso são anuláveis — o banco não tem como exigir que
     * uma manutenção tenha oficina. A regra tem que morar aqui.
     */
    private static void exigirTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensagem);
    }

    private static void exigirValor(java.math.BigDecimal valor, String mensagem) {
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException(mensagem);
    }

    // ---------- Conferência ----------

    /** Fila do gestor: viagens que a telemetria não confirmou. */
    @GetMapping("/retidas")
    public List<ViagemResponse> retidas() {
        sessao.exigirGestor();
        return service.retidas();
    }

    @PostMapping("/{id}/liberar")
    public ViagemResponse liberar(@PathVariable UUID id,
                                  @RequestBody(required = false) Map<String, Object> body) {
        sessao.exigirGestor();
        String obs = body == null ? null : (String) body.get("observacao");
        return service.liberar(id, usuarios.findById(sessao.id()).orElseThrow(), obs);
    }
}
