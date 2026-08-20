package com.lktransportes.repository;

import com.lktransportes.model.MapaConhecido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MapaConhecidoRepository extends JpaRepository<MapaConhecido, UUID> {
}
