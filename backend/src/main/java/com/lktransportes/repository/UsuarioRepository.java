package com.lktransportes.repository;

import com.lktransportes.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.id = ?1")
    Optional<Usuario> bloquear(UUID id);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByTokenTelemetria(String tokenTelemetria);
    Optional<Usuario> findByDiscordId(String discordId);
}
