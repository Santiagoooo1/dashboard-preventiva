package com.preventiva.backend.repository;

import com.preventiva.backend.entity.PlantillaImportacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantillaImportacionRepository extends JpaRepository<PlantillaImportacion, Long> {

    List<PlantillaImportacion> findByDatasetIdAndActivaTrue(Long datasetId);
}
