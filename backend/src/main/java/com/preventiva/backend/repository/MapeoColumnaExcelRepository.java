package com.preventiva.backend.repository;

import java.util.Optional;
import com.preventiva.backend.entity.MapeoColumnaExcel;
import com.preventiva.backend.entity.PlantillaExcel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapeoColumnaExcelRepository extends JpaRepository<MapeoColumnaExcel, Long> {

    List<MapeoColumnaExcel> findByPlantillaAndActivaTrue(PlantillaExcel plantilla);

    List<MapeoColumnaExcel> findByPlantillaIdAndActivaTrue(Long plantillaId);

    boolean existsByPlantillaIdAndNombreColumnaExcelIgnoreCase(Long plantillaId, String nombreColumnaExcel);

    Optional<MapeoColumnaExcel> findByPlantillaIdAndNombreColumnaExcelIgnoreCase(
            Long plantillaId,
            String nombreColumnaExcel);
}