package com.lktransportes.repository;

import com.lktransportes.model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AvisoRepository extends JpaRepository<Aviso, UUID> {
}
