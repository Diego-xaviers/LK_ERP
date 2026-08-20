package com.lktransportes.repository;

import com.lktransportes.model.DocumentoViagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentoViagemRepository extends JpaRepository<DocumentoViagem, UUID> {

    List<DocumentoViagem> findByViagemId(UUID viagemId);

    @Query("select coalesce(max(d.numero), 0) + 1 from DocumentoViagem d where d.tipo = :tipo")
    int proximoNumero(@Param("tipo") DocumentoViagem.TipoDocumento tipo);
}
