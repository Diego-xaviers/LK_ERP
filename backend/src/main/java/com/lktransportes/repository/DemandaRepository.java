package com.lktransportes.repository;

import com.lktransportes.model.Demanda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DemandaRepository extends JpaRepository<Demanda, UUID> {

    List<Demanda> findAllByOrderByCriadaEmDesc();

    List<Demanda> findByStatusOrderByCriadaEmDesc(Demanda.Status status);

    @Query("select coalesce(max(d.numero), 100) from Demanda d")
    Integer ultimoNumero();
}
