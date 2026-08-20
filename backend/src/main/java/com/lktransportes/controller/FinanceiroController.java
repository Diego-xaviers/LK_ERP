package com.lktransportes.controller;

import com.lktransportes.model.*;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.security.SessaoAtual;
import com.lktransportes.service.FinanceiroService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/financeiro")
public class FinanceiroController {

    private final FinanceiroService service;
    private final UsuarioRepository usuarios;
    private final SessaoAtual sessao;

    public FinanceiroController(FinanceiroService service, UsuarioRepository usuarios, SessaoAtual sessao) {
        this.service = service;
        this.usuarios = usuarios;
        this.sessao = sessao;
    }

    /** Caixa, extrato e o que falta acertar com cada motorista — visão do gestor. */
    @GetMapping("/painel")
    public Map<String, Object> painel() {
        sessao.exigirGestor();
        Caixa c = service.caixa();

        List<Map<String, Object>> porMotorista = new ArrayList<>();
        Map<UUID, List<Viagem>> agrupado = new LinkedHashMap<>();
        for (Viagem v : service.pagaveis(null)) {
            agrupado.computeIfAbsent(v.getMotorista().getId(), k -> new ArrayList<>()).add(v);
        }
        for (var entrada : agrupado.entrySet()) {
            Usuario m = usuarios.findById(entrada.getKey()).orElseThrow();
            porMotorista.add(resumoDoMotorista(m, entrada.getValue()));
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("saldo", c.getSaldo());
        resposta.put("valorKmPadrao", c.getValorKmPadrao());
        resposta.put("aPagar", porMotorista.stream()
                .map(x -> (BigDecimal) x.get("comissaoTotal"))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        resposta.put("motoristas", porMotorista);
        resposta.put("extrato", service.extrato(40).stream().map(this::movimentoComoMapa).toList());
        return resposta;
    }

    /** Ganhos do próprio motorista. Ele vê os dele; gestor vê de qualquer um. */
    @GetMapping("/meus-ganhos/{motoristaId}")
    public Map<String, Object> meusGanhos(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        Usuario m = usuarios.findById(motoristaId).orElseThrow();

        Map<String, Object> resposta = new LinkedHashMap<>(resumoDoMotorista(m, service.pagaveis(motoristaId)));
        resposta.put("pagamentos", service.pagamentosDe(motoristaId).stream()
                .map(this::pagamentoComoMapa).toList());
        resposta.put("jaRecebido", service.pagamentosDe(motoristaId).stream()
                .map(Pagamento::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
        return resposta;
    }

    @PostMapping("/pagar/{motoristaId}")
    public Map<String, Object> pagar(@PathVariable UUID motoristaId,
                                     @RequestBody(required = false) Map<String, Object> body) {
        sessao.exigirGestor();
        @SuppressWarnings("unchecked")
        List<String> ids = body == null ? null : (List<String>) body.get("viagemIds");
        String obs = body == null ? null : (String) body.get("observacao");

        Pagamento p = service.pagar(motoristaId,
                ids == null ? null : ids.stream().map(UUID::fromString).toList(),
                obs, usuarios.findById(sessao.id()).orElseThrow());
        return pagamentoComoMapa(p);
    }

    @PostMapping("/ajuste")
    public Map<String, Object> ajustar(@RequestBody Map<String, Object> body) {
        sessao.exigirGestor();
        BigDecimal valor = new BigDecimal(String.valueOf(body.get("valor")));
        Caixa c = service.ajustar(valor, (String) body.get("descricao"),
                usuarios.findById(sessao.id()).orElseThrow());
        return Map.of("saldo", c.getSaldo());
    }

    @PostMapping("/valor-km-padrao")
    public Map<String, Object> valorKmPadrao(@RequestBody Map<String, Object> body) {
        sessao.exigirGestor();
        Caixa c = service.definirValorKmPadrao(new BigDecimal(String.valueOf(body.get("valorKm"))));
        return Map.of("valorKmPadrao", c.getValorKmPadrao());
    }

    /** Valor por km específico de um motorista. Vazio volta para o padrão da empresa. */
    @PostMapping("/valor-km/{motoristaId}")
    public Map<String, Object> valorKmDoMotorista(@PathVariable UUID motoristaId,
                                                  @RequestBody Map<String, Object> body) {
        sessao.exigirGestor();
        Usuario m = usuarios.findById(motoristaId).orElseThrow();
        Object valor = body.get("valorKm");
        m.setValorKmComissao(valor == null || String.valueOf(valor).isBlank()
                ? null : new BigDecimal(String.valueOf(valor)));
        usuarios.save(m);
        return Map.of("valorKm", service.valorKmDe(m));
    }

    // ------------------------------------------------------------------

    private Map<String, Object> resumoDoMotorista(Usuario m, List<Viagem> pagaveis) {
        BigDecimal valorKm = service.valorKmDe(m);
        List<Map<String, Object>> linhas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalKm = BigDecimal.ZERO;

        for (Viagem v : pagaveis) {
            BigDecimal comissao = service.comissaoDe(v, valorKm);
            BigDecimal km = service.kmDe(v);
            total = total.add(comissao);
            totalKm = totalKm.add(km);

            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("id", v.getId());
            linha.put("numero", v.getNumero());
            linha.put("origem", v.getOrigem());
            linha.put("destino", v.getDestino());
            linha.put("carga", v.getCarga());
            linha.put("finalizadaEm", v.getFinalizadaEm());
            linha.put("valorFrete", v.getValorFrete());
            linha.put("despesas", v.totalDespesas());
            linha.put("base", service.baseDe(v));
            // Sem telemetria não há km confirmado — e o gestor precisa ver isso,
            // senão a comissão zerada parece defeito em vez de regra.
            linha.put("km", km);
            linha.put("comissao", comissao);
            linha.put("conferencia", v.getConferencia() == null ? null : v.getConferencia().name());
            linhas.add(linha);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("motoristaId", m.getId());
        r.put("motorista", m.getNome());
        r.put("valorKm", valorKm);
        r.put("valorKmProprio", m.getValorKmComissao());
        r.put("viagens", linhas);
        r.put("kmTotal", totalKm);
        r.put("comissaoTotal", total);
        return r;
    }

    private Map<String, Object> pagamentoComoMapa(Pagamento p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("numero", p.getNumero());
        m.put("motorista", p.getMotorista().getNome());
        m.put("valor", p.getValor());
        m.put("valorKmAplicado", p.getValorKmAplicado());
        m.put("baseKm", p.getBaseKm());
        m.put("baseFrete", p.getBaseFrete());
        m.put("baseDespesas", p.getBaseDespesas());
        m.put("criadoEm", p.getCriadoEm());
        m.put("criadoPor", p.getCriadoPor() == null ? null : p.getCriadoPor().getNome());
        m.put("observacao", p.getObservacao());
        return m;
    }

    private Map<String, Object> movimentoComoMapa(MovimentoCaixa mc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mc.getId());
        m.put("tipo", mc.getTipo().name());
        m.put("entrada", mc.entrada());
        m.put("valor", mc.getValor());
        m.put("descricao", mc.getDescricao());
        m.put("saldoDepois", mc.getSaldoDepois());
        m.put("criadoEm", mc.getCriadoEm());
        return m;
    }
}
