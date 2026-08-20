package com.lktransportes.repository;

import com.lktransportes.model.ItemLoja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ItemLojaRepository extends JpaRepository<ItemLoja, UUID> {
    List<ItemLoja> findAllByOrderByCategoriaAscNomeAsc();
}
