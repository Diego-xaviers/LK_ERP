package com.lktransportes.repository;

import com.lktransportes.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    List<Pagamento> findByMotoristaIdOrderByCriadoEmDesc(UUID motoristaId);
    List<Pagamento> findAllByOrderByCriadoEmDesc();

    @Query("select coalesce(max(p.numero), 500) from Pagamento p")
    Integer ultimoNumero();
}
