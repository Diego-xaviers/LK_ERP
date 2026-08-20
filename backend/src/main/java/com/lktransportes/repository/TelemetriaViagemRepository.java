package com.lktransportes.repository;

import com.lktransportes.model.TelemetriaViagem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TelemetriaViagemRepository extends JpaRepository<TelemetriaViagem, UUID> {
    Optional<TelemetriaViagem> findByViagemId(UUID viagemId);
}
