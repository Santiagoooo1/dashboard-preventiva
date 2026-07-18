package com.preventiva.backend.repository;

import com.preventiva.backend.entity.MetricaClinica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetricaClinicaRepository extends JpaRepository<MetricaClinica, Long> {

    List<MetricaClinica> findByDatasetIdAndActivaTrue(Long datasetId);

    Optional<MetricaClinica> findByDatasetIdAndCodigoIgnoreCaseAndActivaTrue(Long datasetId, String codigo);
}
