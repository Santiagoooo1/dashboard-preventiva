package com.preventiva.backend.repository;

import com.preventiva.backend.entity.ImportacionGenerica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportacionGenericaRepository extends JpaRepository<ImportacionGenerica, Long> {

    List<ImportacionGenerica> findByPlantillaId(Long plantillaId);
}
