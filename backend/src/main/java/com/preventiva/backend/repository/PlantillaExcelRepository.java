package com.preventiva.backend.repository;

import com.preventiva.backend.entity.PlantillaExcel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantillaExcelRepository extends JpaRepository<PlantillaExcel, Long> {

    Optional<PlantillaExcel> findByCodigo(String codigo);

    List<PlantillaExcel> findByActivaTrue();
}