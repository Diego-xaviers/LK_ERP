package com.lktransportes.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Item da loja da transportadora. Tudo aqui é cadastrado pelo gestor — nome,
 * preço, categoria, estoque.
 *
 * A loja é de roleplay: comprar registra o gasto e o histórico, e não muda nada
 * na operação sozinha. O que o item significa é combinado fora do sistema.
 */
@Entity
@Table(name = "itens_loja")
public class ItemLoja {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 600)
    private String descricao;

    @Column(length = 60)
    private String categoria;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal preco;

    /** Nulo = estoque ilimitado. Zero = esgotado. */
    private Integer estoque;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Imagem opcional em base64, no mesmo esquema da foto do perfil. */
    @Lob
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONGVARCHAR)
    @Column(name = "imagem_base64", columnDefinition = "text")
    private String imagemBase64;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public boolean disponivel() {
        return ativo && (estoque == null || estoque > 0);
    }

    /** Baixa o estoque de quem tem estoque; ilimitado não muda. */
    public void baixar(int quantidade) {
        if (!ativo) {
            throw new IllegalStateException("O item \"" + nome + "\" não está à venda.");
        }
        if (estoque != null) {
            if (estoque < quantidade) {
                throw new IllegalStateException(
                        "Estoque insuficiente de \"%s\": restam %d.".formatted(nome, estoque));
            }
            estoque -= quantidade;
        }
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String v) { this.nome = v; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String v) { this.descricao = v; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String v) { this.categoria = v; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal v) { this.preco = v; }
    public Integer getEstoque() { return estoque; }
    public void setEstoque(Integer v) { this.estoque = v; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean v) { this.ativo = v; }
    public String getImagemBase64() { return imagemBase64; }
    public void setImagemBase64(String v) { this.imagemBase64 = v; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
