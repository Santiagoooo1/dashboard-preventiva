package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ComparativaRequestDto;
import com.preventiva.backend.dto.ComparativaResponseDto;
import com.preventiva.backend.dto.ConfiguracionWidgetDto;
import com.preventiva.backend.dto.DashboardDatasetInfoDto;
import com.preventiva.backend.dto.DashboardFiltrosAplicadosDto;
import com.preventiva.backend.dto.DashboardPanelInfoDto;
import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardPanelResponseDto;
import com.preventiva.backend.dto.DashboardPanelResumenDto;
import com.preventiva.backend.dto.DashboardWidgetDto;
import com.preventiva.backend.dto.DashboardWidgetErrorDto;
import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;
import com.preventiva.backend.dto.SerieTemporalRequestDto;
import com.preventiva.backend.dto.SerieTemporalResponseDto;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.enums.EstadoWidgetDashboard;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.service.interfaces.DashboardPanelService;
import com.preventiva.backend.service.interfaces.MetricaAnaliticaService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DashboardPanelServiceImpl implements DashboardPanelService {

    private final PanelClinicoRepository panelClinicoRepository;
    private final PanelMetricaRepository panelMetricaRepository;
    private final MetricaClinicaService metricaClinicaService;
    private final MetricaAnaliticaService metricaAnaliticaService;

    @Override
    public DashboardPanelResponseDto obtenerDashboard(Long panelId, DashboardPanelRequestDto request) {
        PanelClinico panel = obtenerPanelOLanzar(panelId);

        if (!Boolean.TRUE.equals(panel.getActivo())) {
            throw new IllegalArgumentException("El panel está desactivado.");
        }

        DashboardPanelRequestDto filtros = request != null ? request : new DashboardPanelRequestDto();

        List<DashboardWidgetDto> widgets = panelMetricaRepository.findByPanelIdAndActivaTrue(panelId)
                .stream()
                .filter(pm -> Boolean.TRUE.equals(pm.getMetrica().getActiva()))
                .sorted(Comparator.comparing(PanelMetrica::getOrden))
                .map(pm -> construirWidget(pm, filtros))
                .toList();

        long widgetsOk = widgets.stream()
                .filter(w -> EstadoWidgetDashboard.OK.name().equals(w.getEstado()))
                .count();

        DashboardPanelResumenDto resumen = DashboardPanelResumenDto.builder()
                .totalWidgets(widgets.size())
                .widgetsOk((int) widgetsOk)
                .widgetsConError(widgets.size() - (int) widgetsOk)
                .build();

        return DashboardPanelResponseDto.builder()
                .panel(DashboardPanelInfoDto.builder()
                        .id(panel.getId())
                        .codigo(panel.getCodigo())
                        .nombre(panel.getNombre())
                        .descripcion(panel.getDescripcion())
                        .build())
                .dataset(DashboardDatasetInfoDto.builder()
                        .id(panel.getDataset().getId())
                        .codigo(panel.getDataset().getCodigo())
                        .nombre(panel.getDataset().getNombre())
                        .build())
                .filtrosAplicados(DashboardFiltrosAplicadosDto.builder()
                        .fechaDesde(filtros.getFechaDesde())
                        .fechaHasta(filtros.getFechaHasta())
                        .granularidad(filtros.getGranularidad() != null ? filtros.getGranularidad().name() : null)
                        .filtros(filtros.getFiltros())
                        .build())
                .widgets(widgets)
                .resumen(resumen)
                .build();
    }

    private DashboardWidgetDto construirWidget(PanelMetrica pm, DashboardPanelRequestDto filtros) {
        MetricaClinica metrica = pm.getMetrica();
        String titulo = pm.getTituloPersonalizado() != null ? pm.getTituloPersonalizado() : metrica.getNombre();

        TipoResultadoWidget tipoResultado = resolverTipoResultado(pm);

        DashboardWidgetDto.DashboardWidgetDtoBuilder builder = DashboardWidgetDto.builder()
                .panelMetricaId(pm.getId())
                .metricaId(metrica.getId())
                .codigo(metrica.getCodigo())
                .titulo(titulo)
                .descripcion(pm.getDescripcionPersonalizada())
                .tipoVisualizacion(pm.getTipoVisualizacion().name())
                .tipoResultado(tipoResultado.name())
                .orden(pm.getOrden())
                .ancho(pm.getAncho());

        try {
            switch (tipoResultado) {
                case ACTUAL -> builder.resultadoActual(ejecutarActual(metrica.getId(), filtros));
                case SERIE_TEMPORAL -> builder.serieTemporal(ejecutarSerieTemporal(pm, metrica, filtros));
                case COMPARATIVA -> builder.comparativa(ejecutarComparativa(pm, metrica, filtros));
            }
            builder.estado(EstadoWidgetDashboard.OK.name());
        } catch (Exception ex) {
            String mensaje = ex.getMessage() != null ? ex.getMessage() : "Error inesperado al calcular el widget.";
            builder.estado(EstadoWidgetDashboard.ERROR.name())
                    .error(DashboardWidgetErrorDto.builder().mensaje(mensaje).build());
        }

        return builder.build();
    }

    private TipoResultadoWidget resolverTipoResultado(PanelMetrica pm) {
        if (pm.getTipoResultadoWidget() != null) {
            return pm.getTipoResultadoWidget();
        }

        TipoVisualizacion tipoVisualizacion = pm.getTipoVisualizacion();
        TipoMetrica tipoMetrica = pm.getMetrica().getTipoMetrica();
        ConfiguracionWidgetDto config = pm.getConfiguracionWidget();

        return switch (tipoVisualizacion) {
            case KPI, TARJETA -> TipoResultadoWidget.ACTUAL;
            case LINEAS -> tipoMetrica == TipoMetrica.DISTRIBUCION
                    ? TipoResultadoWidget.ACTUAL
                    : TipoResultadoWidget.SERIE_TEMPORAL;
            case BARRAS, TABLA, DONUT, PIE -> {
                if (tipoMetrica == TipoMetrica.DISTRIBUCION) {
                    yield TipoResultadoWidget.ACTUAL;
                }
                String campoAgrupacion = config != null ? config.getCampoAgrupacion() : null;
                yield (campoAgrupacion != null && !campoAgrupacion.isBlank())
                        ? TipoResultadoWidget.COMPARATIVA
                        : TipoResultadoWidget.ACTUAL;
            }
        };
    }

    private ResultadoMetricaResponseDto ejecutarActual(Long metricaId, DashboardPanelRequestDto filtros) {
        EjecucionMetricaRequestDto request = new EjecucionMetricaRequestDto();
        request.setFechaDesde(filtros.getFechaDesde());
        request.setFechaHasta(filtros.getFechaHasta());
        request.setFiltrosGlobales(filtros.getFiltros());

        return metricaClinicaService.ejecutar(metricaId, request);
    }

    private SerieTemporalResponseDto ejecutarSerieTemporal(
            PanelMetrica pm, MetricaClinica metrica, DashboardPanelRequestDto filtros) {
        ConfiguracionWidgetDto config = pm.getConfiguracionWidget();

        Granularidad granularidad = config != null && config.getGranularidad() != null
                ? config.getGranularidad()
                : filtros.getGranularidad();

        if (granularidad == null) {
            throw new IllegalArgumentException(
                    "No se ha especificado granularidad para el widget '" + metrica.getCodigo() + "'.");
        }

        String campoFecha = config != null && config.getCampoFecha() != null
                ? config.getCampoFecha()
                : filtros.getCampoFecha();

        SerieTemporalRequestDto request = new SerieTemporalRequestDto();
        request.setFechaDesde(filtros.getFechaDesde());
        request.setFechaHasta(filtros.getFechaHasta());
        request.setGranularidad(granularidad);
        request.setCampoFecha(campoFecha);
        request.setCampoSegmentacion(config != null ? config.getCampoSegmentacion() : null);
        request.setFiltrosGlobales(filtros.getFiltros());

        return metricaAnaliticaService.serieTemporal(metrica.getId(), request);
    }

    private ComparativaResponseDto ejecutarComparativa(
            PanelMetrica pm, MetricaClinica metrica, DashboardPanelRequestDto filtros) {
        ConfiguracionWidgetDto config = pm.getConfiguracionWidget();
        String campoAgrupacion = config != null ? config.getCampoAgrupacion() : null;

        if (campoAgrupacion == null || campoAgrupacion.isBlank()) {
            throw new IllegalArgumentException(
                    "No se ha especificado campoAgrupacion para el widget '" + metrica.getCodigo() + "'.");
        }

        ComparativaRequestDto request = new ComparativaRequestDto();
        request.setFechaDesde(filtros.getFechaDesde());
        request.setFechaHasta(filtros.getFechaHasta());
        request.setCampoAgrupacion(campoAgrupacion);
        request.setFiltrosGlobales(filtros.getFiltros());

        return metricaAnaliticaService.comparativa(metrica.getId(), request);
    }

    private PanelClinico obtenerPanelOLanzar(Long panelId) {
        return panelClinicoRepository.findById(panelId)
                .orElseThrow(() -> new NoSuchElementException("No existe el panel con id: " + panelId));
    }
}
