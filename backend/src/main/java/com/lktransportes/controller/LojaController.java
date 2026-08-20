package com.lktransportes.controller;

import com.lktransportes.model.*;
import com.lktransportes.repository.UsuarioRepository;
import com.lktransportes.security.SessaoAtual;
import com.lktransportes.service.CarteiraService;
import com.lktransportes.service.LojaService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/loja")
public class LojaController {

    private final LojaService service;
    private final CarteiraService carteira;
    private final UsuarioRepository usuarios;
    private final SessaoAtual sessao;

    public LojaController(LojaService service, CarteiraService carteira,
                          UsuarioRepository usuarios, SessaoAtual sessao) {
        this.service = service;
        this.carteira = carteira;
        this.usuarios = usuarios;
        this.sessao = sessao;
    }

    /** Vitrine. O gestor vê tudo (inclusive inativo e esgotado); motorista só o que dá para comprar. */
    @GetMapping
    public Map<String, Object> vitrine() {
        boolean gestor = sessao.eGestor();
        Usuario eu = usuarios.findById(sessao.id()).orElseThrow();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("meusCreditos", eu.getSaldoCarteira());
        r.put("gestor", gestor);
        r.put("itens", service.catalogo(!gestor).stream().map(this::itemComoMapa).toList());
        return r;
    }

    @PostMapping("/itens")
    public Map<String, Object> criar(@RequestBody Map<String, Object> body) {
        sessao.exigirGestor();
        return itemComoMapa(service.salvar(null, doCorpo(body)));
    }

    @PutMapping("/itens/{id}")
    public Map<String, Object> editar(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        sessao.exigirGestor();
        return itemComoMapa(service.salvar(id, doCorpo(body)));
    }

    @DeleteMapping("/itens/{id}")
    public void remover(@PathVariable UUID id) {
        sessao.exigirGestor();
        service.remover(id);
    }

    /** O motorista compra sempre para si — o id do comprador vem do token. */
    @PostMapping("/comprar/{itemId}")
    public Map<String, Object> comprar(@PathVariable UUID itemId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        int qtd = body == null || body.get("quantidade") == null
                ? 1 : Integer.parseInt(String.valueOf(body.get("quantidade")));
        Compra c = service.comprar(sessao.id(), itemId, qtd);
        Usuario eu = usuarios.findById(sessao.id()).orElseThrow();

        Map<String, Object> r = compraComoMapa(c);
        r.put("meusCreditos", eu.getSaldoCarteira());
        return r;
    }

    @GetMapping("/compras/{motoristaId}")
    public List<Map<String, Object>> compras(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        return service.comprasDe(motoristaId).stream().map(this::compraComoMapa).toList();
    }

    /** Extrato de créditos do motorista. */
    @GetMapping("/carteira/{motoristaId}")
    public Map<String, Object> carteiraDe(@PathVariable UUID motoristaId) {
        sessao.exigirDonoOuGestor(motoristaId);
        Usuario m = usuarios.findById(motoristaId).orElseThrow();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("saldo", m.getSaldoCarteira());
        r.put("movimentos", carteira.extrato(motoristaId).stream().map(mv -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", mv.getId());
            x.put("tipo", mv.getTipo().name());
            x.put("positivo", mv.isPositivo());
            x.put("valor", mv.getValor());
            x.put("descricao", mv.getDescricao());
            x.put("saldoDepois", mv.getSaldoDepois());
            x.put("criadoEm", mv.getCriadoEm());
            return x;
        }).toList());
        return r;
    }

    /** Crédito ou débito de créditos lançado pelo gestor. */
    @PostMapping("/carteira/{motoristaId}/ajuste")
    public Map<String, Object> ajustar(@PathVariable UUID motoristaId, @RequestBody Map<String, Object> body) {
        sessao.exigirGestor();
        Usuario m = usuarios.findById(motoristaId).orElseThrow();
        BigDecimal valor = new BigDecimal(String.valueOf(body.get("valor")));
        String desc = (String) body.get("descricao");

        if (valor.signum() >= 0) {
            carteira.creditar(m, valor, MovimentoCarteira.Tipo.AJUSTE,
                    desc == null || desc.isBlank() ? "Crédito da gestão" : desc);
        } else {
            carteira.debitar(m, valor.abs(), MovimentoCarteira.Tipo.AJUSTE,
                    desc == null || desc.isBlank() ? "Débito da gestão" : desc);
        }
        return Map.of("saldo", usuarios.findById(motoristaId).orElseThrow().getSaldoCarteira());
    }

    // ------------------------------------------------------------------

    private ItemLoja doCorpo(Map<String, Object> body) {
        ItemLoja i = new ItemLoja();
        i.setNome((String) body.get("nome"));
        i.setDescricao((String) body.get("descricao"));
        i.setCategoria((String) body.get("categoria"));
        i.setPreco(new BigDecimal(String.valueOf(body.getOrDefault("preco", "0"))));
        Object estoque = body.get("estoque");
        i.setEstoque(estoque == null || String.valueOf(estoque).isBlank()
                ? null : Integer.parseInt(String.valueOf(estoque)));
        i.setAtivo(body.get("ativo") == null || Boolean.parseBoolean(String.valueOf(body.get("ativo"))));
        i.setImagemBase64((String) body.get("imagemBase64"));
        return i;
    }

    private Map<String, Object> itemComoMapa(ItemLoja i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("nome", i.getNome());
        m.put("descricao", i.getDescricao());
        m.put("categoria", i.getCategoria());
        m.put("preco", i.getPreco());
        m.put("estoque", i.getEstoque());
        m.put("ativo", i.isAtivo());
        m.put("disponivel", i.disponivel());
        m.put("imagemBase64", i.getImagemBase64());
        return m;
    }

    private Map<String, Object> compraComoMapa(Compra c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("motorista", c.getMotorista().getNome());
        m.put("nomeItem", c.getNomeItem());
        m.put("quantidade", c.getQuantidade());
        m.put("valorUnitario", c.getValorUnitario());
        m.put("valorTotal", c.getValorTotal());
        m.put("criadoEm", c.getCriadoEm());
        return m;
    }
}
