package com.lktransportes.repository;

import com.lktransportes.model.EmpresaParceira;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EmpresaParceiraRepository extends JpaRepository<EmpresaParceira, UUID> {
}
