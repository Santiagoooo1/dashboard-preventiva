package com.preventiva.backend.repository;

import com.preventiva.backend.entity.ImportacionTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportacionTrabajoRepository extends JpaRepository<ImportacionTrabajo, Long> {

    List<ImportacionTrabajo> findByDatasetId(Long datasetId);

    Optional<ImportacionTrabajo> findByImportacionGenericaId(Long importacionGenericaId);
}
