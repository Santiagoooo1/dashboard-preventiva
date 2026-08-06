package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.AplicacionDashboardIlqDto;
import com.preventiva.backend.dto.CompatibilidadDashboardIlqDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.MetricaClinicaRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.service.interfaces.PlantillaDashboardIlqService;
import com.preventiva.backend.util.PlantillaDashboardIlq;
import com.preventiva.backend.util.PlantillaDashboardIlq.ElementoPlantilla;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aplica el dashboard clínico de ILQ desde la aplicación (Fase 6.9I.4).
 *
 * <p>Hace lo mismo que {@code scripts/demo/seed-dashboard-mvp-ilq.sql}, pero
 * leyendo la definición tipada de {@link PlantillaDashboardIlq} en vez de
 * repetir las configuraciones. El script sigue existiendo para poder preparar
 * la demo sin arrancar la aplicación, y
 * {@code verify-dashboard-mvp-ilq.sql} valida el resultado de cualquiera de los
 * dos caminos por igual.
 *
 * <p><b>Idempotente</b>: actualiza lo que ya existe y crea lo que falta,
 * resolviendo todo por código estable. Conserva los ids, de modo que el enlace
 * al dashboard sigue siendo válido tras actualizar.
 *
 * <p><b>Alcance estricto</b>: solo toca elementos cuyo código empieza por
 * {@code mvp_ilq_} dentro del dataset indicado. No borra nada.
 */
@Service
@RequiredArgsConstructor
public class PlantillaDashboardIlqServiceImpl implements PlantillaDashboardIlqService {

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final MetricaClinicaRepository metricaClinicaRepository;
    private final PanelClinicoRepository panelClinicoRepository;
    private final PanelMetricaRepository panelMetricaRepository;

    @Override
    @Transactional(readOnly = true)
    public CompatibilidadDashboardIlqDto comprobarCompatibilidad(Long datasetId) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);
        Set<String> activos = codigosDeCamposActivos(datasetId);

        List<String> encontrados = PlantillaDashboardIlq.CAMPOS_ESENCIALES.stream()
                .filter(activos::contains).toList();
        List<String> ausentes = PlantillaDashboardIlq.CAMPOS_ESENCIALES.stream()
                .filter(c -> !activos.contains(c)).toList();

        Optional<PanelClinico> panel = buscarPanel(datasetId);

        return CompatibilidadDashboardIlqDto.builder()
                .datasetId(dataset.getId())
                .datasetCodigo(dataset.getCodigo())
                .compatible(ausentes.isEmpty())
                .yaAplicado(panel.isPresent())
                .panelId(panel.map(PanelClinico::getId).orElse(null))
                .camposEncontrados(encontrados)
                .camposAusentes(ausentes)
                .totalElementos(PlantillaDashboardIlq.elementos().size())
                .build();
    }

    @Override
    @Transactional
    public AplicacionDashboardIlqDto aplicar(Long datasetId) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);

        if (!Boolean.TRUE.equals(dataset.getActivo())) {
            throw new IllegalArgumentException("El dataset está desactivado.");
        }

        Set<String> activos = codigosDeCamposActivos(datasetId);
        List<String> ausentes = PlantillaDashboardIlq.CAMPOS_ESENCIALES.stream()
                .filter(c -> !activos.contains(c)).toList();

        // Todo o nada: aplicar a medias dejaría widgets rotos y un dashboard
        // que parece funcionar pero miente por omisión.
        if (!ausentes.isEmpty()) {
            throw new IllegalArgumentException(
                    "El dataset no contiene todos los campos necesarios. Faltan: " + String.join(", ", ausentes));
        }

        boolean panelCreado = buscarPanel(datasetId).isEmpty();
        PanelClinico panel = obtenerOCrearPanel(dataset);

        Map<String, MetricaClinica> metricasPorCodigo = metricaClinicaRepository.findByDatasetId(datasetId)
                .stream()
                .filter(m -> m.getCodigo().startsWith(PlantillaDashboardIlq.PREFIJO))
                .collect(Collectors.toMap(MetricaClinica::getCodigo, m -> m, (a, b) -> a));

        Map<Long, PanelMetrica> widgetsPorMetrica = panelMetricaRepository.findByPanelId(panel.getId())
                .stream()
                .collect(Collectors.toMap(pm -> pm.getMetrica().getId(), pm -> pm, (a, b) -> a));

        int metricasCreadas = 0;
        int metricasActualizadas = 0;
        int widgetsCreados = 0;
        int widgetsActualizados = 0;

        for (ElementoPlantilla elemento : PlantillaDashboardIlq.elementos()) {
            MetricaClinica metrica = metricasPorCodigo.get(elemento.codigo());

            if (metrica == null) {
                metrica = MetricaClinica.builder().dataset(dataset).codigo(elemento.codigo()).build();
                metricasCreadas++;
            } else {
                metricasActualizadas++;
            }

            // La fórmula clínica SÍ se actualiza: es lo que la plantilla aporta
            // y lo que debe poder corregirse al reaplicarla.
            metrica.setNombre(elemento.nombre());
            metrica.setDescripcion(elemento.descripcion());
            metrica.setTipoMetrica(elemento.tipoMetrica());
            metrica.setConfiguracion(elemento.configuracion());
            metrica.setUnidad(elemento.unidad());
            metrica.setDecimales(elemento.decimales());
            metrica.setOrden(elemento.orden());
            metrica.setActiva(true);

            metrica = metricaClinicaRepository.save(metrica);

            PanelMetrica widget = widgetsPorMetrica.get(metrica.getId());

            if (widget == null) {
                widget = PanelMetrica.builder().panel(panel).metrica(metrica).build();
                widgetsCreados++;
            } else {
                widgetsActualizados++;
            }

            widget.setTipoVisualizacion(elemento.tipoVisualizacion());
            widget.setAncho(elemento.ancho());
            widget.setOrden(elemento.orden());
            widget.setTipoResultadoWidget(elemento.tipoResultado());
            widget.setConfiguracionWidget(elemento.configuracionWidget());
            widget.setActiva(true);

            // El título personalizado del usuario se conserva. La plantilla
            // corrige la fórmula, no cómo el servicio decidió llamar a su
            // widget: rebautizarlo cada vez que se reaplique sería deshacer su
            // trabajo sin avisar.
            if (widget.getTituloPersonalizado() == null) {
                widget.setTituloPersonalizado(elemento.tituloWidget());
            }

            panelMetricaRepository.save(widget);
        }

        long totalMetricas = metricaClinicaRepository.findByDatasetIdAndActivaTrue(datasetId).stream()
                .filter(m -> m.getCodigo().startsWith(PlantillaDashboardIlq.PREFIJO))
                .count();
        long totalWidgets = panelMetricaRepository.findByPanelIdAndActivaTrue(panel.getId()).size();

        return AplicacionDashboardIlqDto.builder()
                .panelId(panel.getId())
                .panelCodigo(panel.getCodigo())
                .panelNombre(panel.getNombre())
                .panelCreado(panelCreado)
                .metricasCreadas(metricasCreadas)
                .metricasActualizadas(metricasActualizadas)
                .widgetsCreados(widgetsCreados)
                .widgetsActualizados(widgetsActualizados)
                .totalMetricas((int) totalMetricas)
                .totalWidgets((int) totalWidgets)
                .build();
    }

    // ------------------------------------------------------------------

    private PanelClinico obtenerOCrearPanel(DatasetClinico dataset) {
        PanelClinico panel = buscarPanel(dataset.getId())
                .orElseGet(() -> PanelClinico.builder()
                        .dataset(dataset)
                        .codigo(PlantillaDashboardIlq.CODIGO_PANEL)
                        .build());

        panel.setNombre(PlantillaDashboardIlq.NOMBRE_PANEL);
        panel.setDescripcion(PlantillaDashboardIlq.DESCRIPCION_PANEL);
        // Orden 0: es el panel principal del dataset, el que abre el acceso
        // directo de la página del dataset.
        panel.setOrden(0);
        panel.setActivo(true);

        return panelClinicoRepository.save(panel);
    }

    private Optional<PanelClinico> buscarPanel(Long datasetId) {
        return panelClinicoRepository.findByDatasetId(datasetId).stream()
                .filter(p -> PlantillaDashboardIlq.CODIGO_PANEL.equals(p.getCodigo()))
                .findFirst();
    }

    private Set<String> codigosDeCamposActivos(Long datasetId) {
        return campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId).stream()
                .map(CampoClinico::getCodigo)
                .collect(Collectors.toSet());
    }

    private DatasetClinico obtenerDatasetOLanzar(Long datasetId) {
        return datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));
    }

    /** Solo para los tests: qué códigos define la plantilla. */
    public static List<String> codigosDeLaPlantilla() {
        List<String> codigos = new ArrayList<>();
        for (ElementoPlantilla e : PlantillaDashboardIlq.elementos()) {
            codigos.add(e.codigo());
        }
        return List.copyOf(codigos);
    }
}
