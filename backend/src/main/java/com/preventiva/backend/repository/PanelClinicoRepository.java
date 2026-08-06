package com.preventiva.backend.repository;

import com.preventiva.backend.entity.PanelClinico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PanelClinicoRepository extends JpaRepository<PanelClinico, Long> {

    List<PanelClinico> findByDatasetIdAndActivoTrue(Long datasetId);

    /**
     * Incluye los archivados. Al reaplicar una plantilla hay que encontrar
     * también un panel archivado con el mismo código: crear otro dejaría dos
     * paneles con el mismo código en el dataset.
     */
    List<PanelClinico> findByDatasetId(Long datasetId);

    Optional<PanelClinico> findByDatasetIdAndCodigoIgnoreCaseAndActivoTrue(Long datasetId, String codigo);
}
