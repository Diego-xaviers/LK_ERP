package com.lktransportes.repository;

import com.lktransportes.model.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PerfilRepository extends JpaRepository<Perfil, UUID> {
    Optional<Perfil> findByUsuarioId(UUID usuarioId);
    Optional<Perfil> findBySteamId(String steamId);
}
