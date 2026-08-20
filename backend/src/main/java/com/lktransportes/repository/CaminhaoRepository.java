package com.lktransportes.repository;

import com.lktransportes.model.Caminhao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CaminhaoRepository extends JpaRepository<Caminhao, UUID> {
}
