package com.lktransportes.service;

import com.lktransportes.model.MovimentoCarteira;
import com.lktransportes.model.Usuario;
import com.lktransportes.repository.MovimentoCarteiraRepository;
import com.lktransportes.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Créditos do motorista. É o que o acerto deposita e o que a loja consome.
 *
 * Mesma disciplina do caixa da empresa: o saldo mora no Usuario, mas toda
 * mudança gera uma linha de extrato explicando o motivo.
 */
@Service
public class CarteiraService {

    private final UsuarioRepository usuarios;
    private final MovimentoCarteiraRepository movimentos;

    public CarteiraService(UsuarioRepository usuarios, MovimentoCarteiraRepository movimentos) {
        this.usuarios = usuarios;
        this.movimentos = movimentos;
    }

    @Transactional
    public Usuario creditar(Usuario motorista, BigDecimal valor,
                            MovimentoCarteira.Tipo tipo, String descricao) {
        return mover(motorista, valor, true, tipo, descricao);
    }

    @Transactional
    public Usuario debitar(Usuario motorista, BigDecimal valor,
                           MovimentoCarteira.Tipo tipo, String descricao) {
        BigDecimal saldo = saldo(motorista);
        if (saldo.compareTo(valor) < 0) {
            throw new IllegalStateException(
                    "Créditos insuficientes: você tem R$ %s e precisa de R$ %s."
                            .formatted(saldo.toPlainString(), valor.toPlainString()));
        }
        return mover(motorista, valor, false, tipo, descricao);
    }

    private Usuario mover(Usuario motorista, BigDecimal valor, boolean positivo,
                          MovimentoCarteira.Tipo tipo, String descricao) {
        BigDecimal novo = positivo ? saldo(motorista).add(valor) : saldo(motorista).subtract(valor);
        motorista.setSaldoCarteira(novo);
        usuarios.save(motorista);

        MovimentoCarteira m = new MovimentoCarteira();
        m.setMotorista(motorista);
        m.setTipo(tipo);
        m.setValor(valor);
        m.setPositivo(positivo);
        m.setDescricao(descricao);
        m.setSaldoDepois(novo);
        movimentos.save(m);
        return motorista;
    }

    private BigDecimal saldo(Usuario u) {
        return u.getSaldoCarteira() == null ? BigDecimal.ZERO : u.getSaldoCarteira();
    }

    @Transactional(readOnly = true)
    public List<MovimentoCarteira> extrato(UUID motoristaId) {
        return movimentos.findByMotoristaIdOrderByCriadoEmDesc(motoristaId);
    }
}
