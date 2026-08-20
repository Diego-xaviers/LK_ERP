package com.lktransportes.repository;

import com.lktransportes.model.Carreta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CarretaRepository extends JpaRepository<Carreta, UUID> {
}
