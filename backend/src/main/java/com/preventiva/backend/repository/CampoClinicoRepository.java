package com.preventiva.backend.repository;

import com.preventiva.backend.entity.CampoClinico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampoClinicoRepository extends JpaRepository<CampoClinico, Long> {

    List<CampoClinico> findByDatasetIdAndActivoTrue(Long datasetId);

    boolean existsByDatasetIdAndCodigoIgnoreCase(Long datasetId, String codigo);

    Optional<CampoClinico> findByDatasetIdAndCodigoIgnoreCase(Long datasetId, String codigo);

    void deleteByDatasetId(Long datasetId);
}
