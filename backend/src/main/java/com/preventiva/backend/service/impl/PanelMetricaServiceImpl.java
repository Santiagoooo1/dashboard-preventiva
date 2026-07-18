package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.PanelMetricaRequestDto;
import com.preventiva.backend.dto.PanelMetricaResponseDto;
import com.preventiva.backend.entity.MetricaClinica;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.repository.MetricaClinicaRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.service.interfaces.PanelMetricaService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PanelMetricaServiceImpl implements PanelMetricaService {

    private final PanelMetricaRepository panelMetricaRepository;
    private final PanelClinicoRepository panelClinicoRepository;
    private final MetricaClinicaRepository metricaClinicaRepository;

    @Override
    public List<PanelMetricaResponseDto> listarPorPanel(Long panelId) {
        obtenerPanelOLanzar(panelId);

        return panelMetricaRepository.findByPanelIdAndActivaTrue(panelId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public PanelMetricaResponseDto crear(Long panelId, PanelMetricaRequestDto request) {
        PanelClinico panel = obtenerPanelActivoOLanzar(panelId);
        MetricaClinica metrica = resolverMetrica(panel, request.getMetricaId());

        validarSinDuplicado(panelId, metrica.getId(), null);

        PanelMetrica panelMetrica = PanelMetrica.builder()
                .panel(panel)
                .metrica(metrica)
                .tituloPersonalizado(request.getTituloPersonalizado())
                .descripcionPersonalizada(request.getDescripcionPersonalizada())
                .tipoVisualizacion(resolverTipoVisualizacion(request.getTipoVisualizacion(), metrica.getTipoMetrica()))
                .orden(request.getOrden() != null ? request.getOrden() : 0)
                .ancho(request.getAncho() != null ? request.getAncho() : 3)
                .activa(true)
                .build();

        return mapToDto(panelMetricaRepository.save(panelMetrica));
    }

    @Override
    public PanelMetricaResponseDto actualizar(Long panelId, Long panelMetricaId, PanelMetricaRequestDto request) {
        PanelClinico panel = obtenerPanelActivoOLanzar(panelId);
        PanelMetrica panelMetrica = obtenerPanelMetricaOLanzar(panelId, panelMetricaId);
        MetricaClinica metrica = resolverMetrica(panel, request.getMetricaId());

        validarSinDuplicado(panelId, metrica.getId(), panelMetricaId);

        panelMetrica.setMetrica(metrica);
        panelMetrica.setTituloPersonalizado(request.getTituloPersonalizado());
        panelMetrica.setDescripcionPersonalizada(request.getDescripcionPersonalizada());
        panelMetrica.setTipoVisualizacion(
                resolverTipoVisualizacion(request.getTipoVisualizacion(), metrica.getTipoMetrica()));
        panelMetrica.setOrden(request.getOrden() != null ? request.getOrden() : 0);
        panelMetrica.setAncho(request.getAncho() != null ? request.getAncho() : 3);

        return mapToDto(panelMetricaRepository.save(panelMetrica));
    }

    @Override
    public void desactivar(Long panelId, Long panelMetricaId) {
        obtenerPanelOLanzar(panelId);
        PanelMetrica panelMetrica = obtenerPanelMetricaOLanzar(panelId, panelMetricaId);
        panelMetrica.setActiva(false);
        panelMetricaRepository.save(panelMetrica);
    }

    private void validarSinDuplicado(Long panelId, Long metricaId, Long panelMetricaIdExcluido) {
        boolean duplicado = panelMetricaRepository.findByPanelIdAndMetricaIdAndActivaTrue(panelId, metricaId)
                .filter(pm -> panelMetricaIdExcluido == null || !pm.getId().equals(panelMetricaIdExcluido))
                .isPresent();

        if (duplicado) {
            throw new IllegalArgumentException("Esa métrica ya está activa en este panel.");
        }
    }

    private MetricaClinica resolverMetrica(PanelClinico panel, Long metricaId) {
        MetricaClinica metrica = metricaClinicaRepository.findById(metricaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la métrica con id: " + metricaId));

        if (!Boolean.TRUE.equals(metrica.getActiva())) {
            throw new IllegalArgumentException("La métrica con id " + metricaId + " está desactivada.");
        }

        if (!metrica.getDataset().getId().equals(panel.getDataset().getId())) {
            throw new IllegalArgumentException("La métrica no pertenece al mismo dataset que el panel.");
        }

        return metrica;
    }

    private TipoVisualizacion resolverTipoVisualizacion(TipoVisualizacion solicitado, TipoMetrica tipoMetrica) {
        if (solicitado != null) {
            return solicitado;
        }

        return tipoMetrica == TipoMetrica.DISTRIBUCION ? TipoVisualizacion.BARRAS : TipoVisualizacion.KPI;
    }

    private PanelClinico obtenerPanelOLanzar(Long panelId) {
        return panelClinicoRepository.findById(panelId)
                .orElseThrow(() -> new NoSuchElementException("No existe el panel con id: " + panelId));
    }

    private PanelClinico obtenerPanelActivoOLanzar(Long panelId) {
        PanelClinico panel = obtenerPanelOLanzar(panelId);

        if (!Boolean.TRUE.equals(panel.getActivo())) {
            throw new IllegalArgumentException("El panel está desactivado; no se pueden crear ni editar métricas.");
        }

        return panel;
    }

    private PanelMetrica obtenerPanelMetricaOLanzar(Long panelId, Long panelMetricaId) {
        PanelMetrica panelMetrica = panelMetricaRepository.findById(panelMetricaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la métrica de panel con id: " + panelMetricaId));

        if (!panelMetrica.getPanel().getId().equals(panelId)) {
            throw new NoSuchElementException(
                    "La métrica de panel " + panelMetricaId + " no pertenece al panel " + panelId);
        }

        return panelMetrica;
    }

    private PanelMetricaResponseDto mapToDto(PanelMetrica panelMetrica) {
        return PanelMetricaResponseDto.builder()
                .id(panelMetrica.getId())
                .panelId(panelMetrica.getPanel().getId())
                .metricaId(panelMetrica.getMetrica().getId())
                .metricaCodigo(panelMetrica.getMetrica().getCodigo())
                .metricaNombre(panelMetrica.getMetrica().getNombre())
                .tituloPersonalizado(panelMetrica.getTituloPersonalizado())
                .descripcionPersonalizada(panelMetrica.getDescripcionPersonalizada())
                .tipoVisualizacion(panelMetrica.getTipoVisualizacion().name())
                .orden(panelMetrica.getOrden())
                .ancho(panelMetrica.getAncho())
                .activa(panelMetrica.getActiva())
                .build();
    }
}
