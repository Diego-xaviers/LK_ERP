package com.lktransportes.repository;

import com.lktransportes.model.TelemetriaSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelemetriaSessaoRepository extends JpaRepository<TelemetriaSessao, UUID> {
    Optional<TelemetriaSessao> findByMotoristaId(UUID motoristaId);

    /** Retorna todas as sessões com ping recente (para o painel de frota). */
    @Query("SELECT s FROM TelemetriaSessao s WHERE s.atualizadoEm >= :corte")
    List<TelemetriaSessao> findRecentes(@Param("corte") LocalDateTime corte);
}
