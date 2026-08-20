package com.lktransportes.repository;

import com.lktransportes.model.MovimentoCaixa;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MovimentoCaixaRepository extends JpaRepository<MovimentoCaixa, UUID> {
    List<MovimentoCaixa> findByOrderByCriadoEmDesc(Limit limite);
}
