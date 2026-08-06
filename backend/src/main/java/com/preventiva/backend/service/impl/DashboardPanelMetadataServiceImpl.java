package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.ConfiguracionWidgetDto;
import com.preventiva.backend.dto.DashboardPanelMetadataResponseDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.dto.PanelClinicoResponseDto;
import com.preventiva.backend.dto.WidgetMetadataDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.service.interfaces.DashboardPanelMetadataService;
import com.preventiva.backend.util.CampoRolesUtil;
import com.preventiva.backend.util.OperacionMetricaUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DashboardPanelMetadataServiceImpl implements DashboardPanelMetadataService {

    private static final List<String> TIPOS_RESULTADO_COMPLETOS = List.of(
            TipoResultadoWidget.ACTUAL.name(),
            TipoResultadoWidget.SERIE_TEMPORAL.name(),
            TipoResultadoWidget.COMPARATIVA.name());

    private static final List<String> TIPOS_RESULTADO_SOLO_ACTUAL = List.of(TipoResultadoWidget.ACTUAL.name());

    private final PanelClinicoRepository panelClinicoRepository;
    private final PanelMetricaRepository panelMetricaRepository;
    private final CampoClinicoRepository campoClinicoRepository;

    @Override
    public DashboardPanelMetadataResponseDto obtenerMetadataPanel(Long panelId) {
        PanelClinico panel = obtenerPanelOLanzar(panelId);
        Long datasetId = panel.getDataset().getId();

        List<CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId);

        List<WidgetMetadataDto> widgets = panelMetricaRepository.findByPanelIdAndActivaTrue(panelId)
                .stream()
                .filter(pm -> Boolean.TRUE.equals(pm.getMetrica().getActiva()))
                .sorted(Comparator.comparing(PanelMetrica::getOrden))
                .map(this::mapWidget)
                .toList();

        return DashboardPanelMetadataResponseDto.builder()
                .panel(mapPanel(panel))
                .dataset(mapDataset(panel.getDataset()))
                .camposFechaPermitidos(campos.stream().filter(c -> CampoRolesUtil.esFecha(c.getTipoDato()))
                        .map(CampoClinico::getCodigo).toList())
                .camposAgrupacionPermitidos(campos.stream().filter(c -> CampoRolesUtil.esAgrupable(c.getTipoDato()))
                        .map(CampoClinico::getCodigo).toList())
                .widgets(widgets)
                .build();
    }

    private WidgetMetadataDto mapWidget(PanelMetrica pm) {
        TipoResultadoWidget tipoResultadoActual = resolverTipoResultado(pm);

        String titulo = pm.getTituloPersonalizado() != null ? pm.getTituloPersonalizado() : pm.getMetrica().getNombre();

        return WidgetMetadataDto.builder()
                .panelMetricaId(pm.getId())
                .metricaId(pm.getMetrica().getId())
                .codigo(pm.getMetrica().getCodigo())
                .nombre(titulo)
                .tipoMetrica(pm.getMetrica().getTipoMetrica().name())
                .tipoVisualizacion(pm.getTipoVisualizacion().name())
                .tipoResultadoWidgetConfigurado(
                        pm.getTipoResultadoWidget() != null ? pm.getTipoResultadoWidget().name() : null)
                .tipoResultadoActual(tipoResultadoActual.name())
                .tipoResultadosPermitidos(tipoResultadosPermitidos(pm))
                .configuracionWidgetActual(pm.getConfiguracionWidget())
                .build();
    }

    /**
     * Qué formas de resultado admite ESTE widget.
     *
     * <p>Antes solo se excluía DISTRIBUCION. Desde la Fase 6.9I.2 hay más
     * operaciones cuyo resultado no es un número que se pueda poner en un eje
     * —la categoría más frecuente devuelve una etiqueta, y un mínimo o máximo
     * sobre una fecha devuelve una fecha—, y ofrecerles «agrupar» dejaba un
     * botón activo que abría un formulario sin ninguna opción útil.
     *
     * <p>El criterio es el mismo que usa el motor para rechazarlas
     * ({@link OperacionMetricaUtil#produceValorNumerico}), de modo que lo que
     * se ofrece aquí y lo que el backend acepta no puedan divergir.
     */
    private List<String> tipoResultadosPermitidos(PanelMetrica pm) {
        ConfiguracionMetricaDto config = pm.getMetrica().getConfiguracion();
        String codigoCampoValor = config != null ? config.getCampoValor() : null;

        TipoDatoExcel tipoDatoCampoValor = null;
        if (codigoCampoValor != null) {
            tipoDatoCampoValor = campoClinicoRepository
                    .findByDatasetIdAndActivoTrue(pm.getMetrica().getDataset().getId()).stream()
                    .filter(c -> c.getCodigo().equals(codigoCampoValor))
                    .map(CampoClinico::getTipoDato)
                    .findFirst()
                    .orElse(null);
        }

        return OperacionMetricaUtil.produceValorNumerico(pm.getMetrica().getTipoMetrica(), tipoDatoCampoValor)
                ? TIPOS_RESULTADO_COMPLETOS
                : TIPOS_RESULTADO_SOLO_ACTUAL;
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

    private PanelClinicoResponseDto mapPanel(PanelClinico panel) {
        return PanelClinicoResponseDto.builder()
                .id(panel.getId())
                .datasetId(panel.getDataset().getId())
                .codigo(panel.getCodigo())
                .nombre(panel.getNombre())
                .descripcion(panel.getDescripcion())
                .orden(panel.getOrden())
                .activo(panel.getActivo())
                .build();
    }

    private DatasetClinicoResponseDto mapDataset(DatasetClinico dataset) {
        return DatasetClinicoResponseDto.builder()
                .id(dataset.getId())
                .codigo(dataset.getCodigo())
                .nombre(dataset.getNombre())
                .descripcion(dataset.getDescripcion())
                .hospitalId(dataset.getHospital() != null ? dataset.getHospital().getId() : null)
                .hospitalNombre(dataset.getHospital() != null ? dataset.getHospital().getNombre() : null)
                .activo(dataset.getActivo())
                .build();
    }

    private PanelClinico obtenerPanelOLanzar(Long panelId) {
        return panelClinicoRepository.findById(panelId)
                .orElseThrow(() -> new NoSuchElementException("No existe el panel con id: " + panelId));
    }
}
