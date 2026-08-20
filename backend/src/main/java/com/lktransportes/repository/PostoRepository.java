package com.lktransportes.repository;

import com.lktransportes.model.Posto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PostoRepository extends JpaRepository<Posto, UUID> {
    Optional<Posto> findByNome(String nome);
}
