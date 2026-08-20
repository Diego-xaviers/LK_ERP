package com.lktransportes.repository;

import com.lktransportes.model.TelemetriaSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TelemetriaSessaoRepository extends JpaRepository<TelemetriaSessao, UUID> {
    Optional<TelemetriaSessao> findByMotoristaId(UUID motoristaId);
}
