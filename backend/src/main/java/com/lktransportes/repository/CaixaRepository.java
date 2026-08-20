package com.lktransportes.repository;

import com.lktransportes.model.Caixa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CaixaRepository extends JpaRepository<Caixa, UUID> {
}
