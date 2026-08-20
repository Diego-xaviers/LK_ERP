package com.lktransportes.repository;

import com.lktransportes.model.CidadeMapa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CidadeMapaRepository extends JpaRepository<CidadeMapa, UUID> {
    Optional<CidadeMapa> findByIdJogo(String idJogo);
    List<CidadeMapa> findAllByOrderByNomeAsc();
}
