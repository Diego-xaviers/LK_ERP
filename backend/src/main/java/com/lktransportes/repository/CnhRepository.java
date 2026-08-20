package com.lktransportes.repository;

import com.lktransportes.model.Cnh;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CnhRepository extends JpaRepository<Cnh, UUID> {
    Optional<Cnh> findByMotoristaId(UUID motoristaId);
    List<Cnh> findAllByOrderByValidadeAsc();
}
