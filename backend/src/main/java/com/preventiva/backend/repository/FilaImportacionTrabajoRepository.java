package com.preventiva.backend.repository;

import com.preventiva.backend.entity.FilaImportacionTrabajo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FilaImportacionTrabajoRepository extends JpaRepository<FilaImportacionTrabajo, Long> {

    List<FilaImportacionTrabajo> findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(Long importacionTrabajoId);

    Page<FilaImportacionTrabajo> findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(
            Long importacionTrabajoId, Pageable pageable);

    Optional<FilaImportacionTrabajo> findByImportacionTrabajoIdAndNumeroFilaOriginal(
            Long importacionTrabajoId, Integer numeroFilaOriginal);

    long countByImportacionTrabajoId(Long importacionTrabajoId);

    long countByImportacionTrabajoIdAndExcluidaTrue(Long importacionTrabajoId);

    @Query(value = "SELECT * FROM filas_importacion_trabajo "
            + "WHERE importacion_trabajo_id = :importacionTrabajoId "
            + "AND jsonb_array_length(errores_actuales) > 0 "
            + "ORDER BY numero_fila_original",
            countQuery = "SELECT count(*) FROM filas_importacion_trabajo "
                    + "WHERE importacion_trabajo_id = :importacionTrabajoId "
                    + "AND jsonb_array_length(errores_actuales) > 0",
            nativeQuery = true)
    Page<FilaImportacionTrabajo> findConErrores(
            @Param("importacionTrabajoId") Long importacionTrabajoId, Pageable pageable);

    @Query(value = "SELECT count(*) FROM filas_importacion_trabajo f "
            + "WHERE f.importacion_trabajo_id = :importacionTrabajoId "
            + "AND f.excluida = false "
            + "AND EXISTS (SELECT 1 FROM jsonb_array_elements(f.errores_actuales) e "
            + "WHERE e->>'severidad' = 'ERROR')",
            nativeQuery = true)
    long contarFilasActivasConErrorBloqueante(@Param("importacionTrabajoId") Long importacionTrabajoId);
}
