package com.preventiva.backend.repository;

import com.preventiva.backend.entity.PanelClinico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PanelClinicoRepository extends JpaRepository<PanelClinico, Long> {

    List<PanelClinico> findByDatasetIdAndActivoTrue(Long datasetId);

    Optional<PanelClinico> findByDatasetIdAndCodigoIgnoreCaseAndActivoTrue(Long datasetId, String codigo);
}
