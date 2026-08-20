package com.lktransportes.service;

import com.lktransportes.model.Compra;
import com.lktransportes.model.ItemLoja;
import com.lktransportes.model.MovimentoCarteira;
import com.lktransportes.model.Usuario;
import com.lktransportes.repository.CompraRepository;
import com.lktransportes.repository.ItemLojaRepository;
import com.lktransportes.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Loja da transportadora. O catálogo é inteiramente do gestor: ele cadastra
 * nome, preço, categoria e estoque.
 *
 * É de roleplay — comprar debita os créditos e registra o histórico, e não
 * altera nada na operação por conta própria. O que cada item significa é
 * combinado fora do sistema.
 */
@Service
public class LojaService {

    private final ItemLojaRepository itens;
    private final CompraRepository compras;
    private final UsuarioRepository usuarios;
    private final CarteiraService carteira;

    public LojaService(ItemLojaRepository itens, CompraRepository compras,
                       UsuarioRepository usuarios, CarteiraService carteira) {
        this.itens = itens;
        this.compras = compras;
        this.usuarios = usuarios;
        this.carteira = carteira;
    }

    // ----- Catálogo (gestor) -----

    @Transactional(readOnly = true)
    public List<ItemLoja> catalogo(boolean apenasDisponiveis) {
        List<ItemLoja> todos = itens.findAllByOrderByCategoriaAscNomeAsc();
        return apenasDisponiveis ? todos.stream().filter(ItemLoja::disponivel).toList() : todos;
    }

    @Transactional
    public ItemLoja salvar(UUID id, ItemLoja dados) {
        ItemLoja alvo = id == null ? new ItemLoja() : itens.findById(id).orElseThrow();
        alvo.setNome(dados.getNome());
        alvo.setDescricao(dados.getDescricao());
        alvo.setCategoria(dados.getCategoria());
        alvo.setPreco(dados.getPreco());
        alvo.setEstoque(dados.getEstoque());
        alvo.setAtivo(dados.isAtivo());
        if (dados.getImagemBase64() != null) {
            alvo.setImagemBase64(dados.getImagemBase64().isBlank() ? null : dados.getImagemBase64());
        }
        return itens.save(alvo);
    }

    @Transactional
    public void remover(UUID id) {
        itens.deleteById(id);
    }

    // ----- Compra (motorista) -----

    @Transactional
    public Compra comprar(UUID motoristaId, UUID itemId, int quantidade) {
        if (quantidade < 1) {
            throw new IllegalArgumentException("Quantidade precisa ser pelo menos 1.");
        }
        Usuario motorista = usuarios.findById(motoristaId).orElseThrow();
        ItemLoja item = itens.findById(itemId).orElseThrow();

        BigDecimal total = item.getPreco().multiply(BigDecimal.valueOf(quantidade));

        item.baixar(quantidade);      // recusa se inativo ou sem estoque
        itens.save(item);

        // Debita antes de gravar a compra: sem créditos, nada acontece.
        carteira.debitar(motorista, total, MovimentoCarteira.Tipo.COMPRA,
                "Compra na loja: %dx %s".formatted(quantidade, item.getNome()));

        Compra c = new Compra();
        c.setMotorista(motorista);
        c.setItem(item);
        c.setNomeItem(item.getNome());
        c.setQuantidade(quantidade);
        c.setValorUnitario(item.getPreco());
        c.setValorTotal(total);
        return compras.save(c);
    }

    @Transactional(readOnly = true)
    public List<Compra> comprasDe(UUID motoristaId) {
        return motoristaId == null
                ? compras.findAllByOrderByCriadoEmDesc()
                : compras.findByMotoristaIdOrderByCriadoEmDesc(motoristaId);
    }
}
