package com.lktransportes.repository;

import com.lktransportes.model.EventoViagem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EventoViagemRepository extends JpaRepository<EventoViagem, UUID> {
}
