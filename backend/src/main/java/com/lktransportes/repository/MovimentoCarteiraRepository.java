package com.lktransportes.repository;

import com.lktransportes.model.MovimentoCarteira;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MovimentoCarteiraRepository extends JpaRepository<MovimentoCarteira, UUID> {
    List<MovimentoCarteira> findByMotoristaIdOrderByCriadoEmDesc(UUID motoristaId);
}
