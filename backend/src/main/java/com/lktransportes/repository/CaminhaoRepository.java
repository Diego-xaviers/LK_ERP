package com.lktransportes.repository;

import com.lktransportes.model.Caminhao;
import com.lktransportes.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaminhaoRepository extends JpaRepository<Caminhao, UUID> {

    @Query("SELECT c FROM Caminhao c WHERE UPPER(c.placa) = UPPER(:placa)")
    Optional<Caminhao> findByPlacaIgnoreCase(@Param("placa") String placa);

    List<Caminhao> findByDono(Usuario dono);
}
