package com.preventiva.backend.repository;

import com.preventiva.backend.entity.MapeoCampoImportacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MapeoCampoImportacionRepository extends JpaRepository<MapeoCampoImportacion, Long> {

    List<MapeoCampoImportacion> findByPlantillaIdAndActivoTrue(Long plantillaId);

    boolean existsByPlantillaIdAndNombreColumnaOrigenIgnoreCase(Long plantillaId, String nombreColumnaOrigen);

    Optional<MapeoCampoImportacion> findByPlantillaIdAndNombreColumnaOrigenIgnoreCaseAndActivoTrue(
            Long plantillaId, String nombreColumnaOrigen);

    Optional<MapeoCampoImportacion> findByPlantillaIdAndCampoClinicoIdAndActivoTrue(
            Long plantillaId, Long campoClinicoId);
}
