package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.CatalogoInformeResponseDto;
import com.preventiva.backend.dto.DashboardDisponibleDto;
import com.preventiva.backend.dto.IndicadorDisponibleDto;
import com.preventiva.backend.dto.WidgetDisponibleDto;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.MetricaClinicaRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.service.interfaces.CatalogoInformeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Qué se puede insertar en un informe (Fase 6.9Q.1).
 *
 * <p>La fuente principal son los widgets que el usuario ya tiene montados en
 * sus dashboards, con su configuración exacta. Antes la biblioteca partía de las
 * métricas e inventaba una representación —«gráfica» significaba BARRAS—, así
 * que una «Evolución mensual de ILQ» que en el dashboard es una línea temporal
 * mensual aterrizaba en el informe como unas barras que no se parecían a lo que
 * el médico había aprobado.
 *
 * <p>Es estructural: <b>no ejecuta ninguna métrica</b>. Abrir la biblioteca del
 * editor no debe disparar los cálculos de todos los dashboards del hospital.
 */
@Service
@RequiredArgsConstructor
public class CatalogoInformeServiceImpl implements CatalogoInformeService {

    private final DatasetClinicoRepository datasetRepository;
    private final PanelClinicoRepository panelRepository;
    private final PanelMetricaRepository panelMetricaRepository;
    private final MetricaClinicaRepository metricaRepository;

    @Override
    @Transactional(readOnly = true)
    public CatalogoInformeResponseDto obtener() {
        List<DashboardDisponibleDto> dashboards = new ArrayList<>();
        List<IndicadorDisponibleDto> sueltos = new ArrayList<>();

        for (DatasetClinico dataset : datasetRepository.findByActivoTrue()) {
            Set<Long> metricasEnAlgunPanel = new HashSet<>();

            for (PanelClinico panel : panelRepository.findByDatasetIdAndActivoTrue(dataset.getId())) {
                List<WidgetDisponibleDto> widgets = new ArrayList<>();

                for (PanelMetrica pm
                        : panelMetricaRepository.findByPanelIdAndActivaTrueOrderByOrdenAscIdAsc(panel.getId())) {
                    MetricaClinica metrica = pm.getMetrica();
                    // Mismo criterio que el dashboard: si allí no se pinta,
                    // tampoco se ofrece para insertarlo en un informe.
                    if (metrica == null || !Boolean.TRUE.equals(metrica.getActiva())) {
                        continue;
                    }
                    metricasEnAlgunPanel.add(metrica.getId());

                    widgets.add(WidgetDisponibleDto.builder()
                            .panelMetricaId(pm.getId())
                            .metricaId(metrica.getId())
                            // El título que el usuario lee en su dashboard, no el
                            // código interno de la métrica.
                            .titulo(pm.getTituloPersonalizado() != null
                                    ? pm.getTituloPersonalizado()
                                    : metrica.getNombre())
                            .tituloPersonalizado(pm.getTituloPersonalizado())
                            .tipoVisualizacion(pm.getTipoVisualizacion() != null
                                    ? pm.getTipoVisualizacion().name()
                                    : null)
                            .tipoResultadoWidget(pm.getTipoResultadoWidget() != null
                                    ? pm.getTipoResultadoWidget().name()
                                    : null)
                            .configuracionWidget(pm.getConfiguracionWidget())
                            .ancho(pm.getAncho())
                            .build());
                }

                if (!widgets.isEmpty()) {
                    dashboards.add(DashboardDisponibleDto.builder()
                            .panelId(panel.getId())
                            .panelNombre(panel.getNombre())
                            .datasetId(dataset.getId())
                            .datasetNombre(dataset.getNombre())
                            .widgets(widgets)
                            .build());
                }
            }

            // Lo que existe pero nadie ha puesto en un dashboard: sigue siendo
            // utilizable, pero al insertarlo habrá que elegir representación.
            for (MetricaClinica metrica : metricaRepository.findByDatasetIdAndActivaTrue(dataset.getId())) {
                if (metricasEnAlgunPanel.contains(metrica.getId())) {
                    continue;
                }
                sueltos.add(IndicadorDisponibleDto.builder()
                        .metricaId(metrica.getId())
                        .nombre(metrica.getNombre())
                        .tipoMetrica(metrica.getTipoMetrica().name())
                        .datasetId(dataset.getId())
                        .datasetNombre(dataset.getNombre())
                        .build());
            }
        }

        return CatalogoInformeResponseDto.builder()
                .dashboards(dashboards)
                .indicadoresSinDashboard(sueltos)
                .build();
    }
}
