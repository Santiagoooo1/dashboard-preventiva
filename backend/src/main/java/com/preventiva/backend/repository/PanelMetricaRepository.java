package com.preventiva.backend.repository;

import com.preventiva.backend.entity.PanelMetrica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PanelMetricaRepository extends JpaRepository<PanelMetrica, Long> {

    List<PanelMetrica> findByPanelIdAndActivaTrue(Long panelId);

    Optional<PanelMetrica> findByPanelIdAndMetricaIdAndActivaTrue(Long panelId, Long metricaId);
}
