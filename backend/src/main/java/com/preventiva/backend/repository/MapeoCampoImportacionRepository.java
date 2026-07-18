package com.preventiva.backend.repository;

import com.preventiva.backend.entity.MapeoCampoImportacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapeoCampoImportacionRepository extends JpaRepository<MapeoCampoImportacion, Long> {

    List<MapeoCampoImportacion> findByPlantillaIdAndActivoTrue(Long plantillaId);

    boolean existsByPlantillaIdAndNombreColumnaOrigenIgnoreCase(Long plantillaId, String nombreColumnaOrigen);
}
