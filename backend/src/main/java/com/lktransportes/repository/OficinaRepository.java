package com.lktransportes.repository;

import com.lktransportes.model.Oficina;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OficinaRepository extends JpaRepository<Oficina, UUID> {
}
