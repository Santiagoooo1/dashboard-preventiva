package com.preventiva.backend.repository;

import com.preventiva.backend.entity.PanelMetrica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PanelMetricaRepository extends JpaRepository<PanelMetrica, Long> {

    List<PanelMetrica> findByPanelIdAndActivaTrue(Long panelId);

    /**
     * En el orden en que el usuario los ve en su dashboard.
     *
     * <p>Sin ORDER BY explícito el motor puede devolverlos en cualquier orden, y
     * dos widgets con el mismo <code>orden</code> —lo habitual nada más
     * añadirlos— se intercambiarían entre llamadas. El id desempata por orden de
     * inserción.
     */
    List<PanelMetrica> findByPanelIdAndActivaTrueOrderByOrdenAscIdAsc(Long panelId);

    /** Incluye los archivados, por el mismo motivo que en las métricas. */
    List<PanelMetrica> findByPanelId(Long panelId);

    /** Para avisar de duplicados aunque el widget esté archivado. */
    Optional<PanelMetrica> findByPanelIdAndMetricaId(Long panelId, Long metricaId);

    Optional<PanelMetrica> findByPanelIdAndMetricaIdAndActivaTrue(Long panelId, Long metricaId);
}
