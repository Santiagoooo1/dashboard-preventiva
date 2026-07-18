package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.PanelClinicoResponseDto;
import com.preventiva.backend.dto.PanelEjecucionResponseDto;
import com.preventiva.backend.dto.PanelMetricaResultadoDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.PanelClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.PanelClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.PanelClinicoService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PanelClinicoServiceImpl implements PanelClinicoService {

    private final PanelClinicoRepository panelClinicoRepository;
    private final PanelMetricaRepository panelMetricaRepository;
    private final DatasetClinicoRepository datasetClinicoRepository;
    private final MetricaClinicaService metricaClinicaService;

    @Override
    public List<PanelClinicoResponseDto> listarPorDataset(Long datasetId) {
        obtenerDatasetOLanzar(datasetId);

        return panelClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public PanelClinicoResponseDto obtenerPorId(Long id) {
        return mapToDto(obtenerPanelOLanzar(id));
    }

    @Override
    public PanelClinicoResponseDto crear(Long datasetId, PanelClinicoRequestDto request) {
        DatasetClinico dataset = obtenerDatasetActivoOLanzar(datasetId);

        panelClinicoRepository.findByDatasetIdAndCodigoIgnoreCaseAndActivoTrue(datasetId, request.getCodigo())
                .ifPresent(p -> {
                    throw new IllegalArgumentException(
                            "Ya existe un panel activo con el código: " + request.getCodigo());
                });

        PanelClinico panel = PanelClinico.builder()
                .dataset(dataset)
                .codigo(request.getCodigo())
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .orden(request.getOrden() != null ? request.getOrden() : 0)
                .activo(true)
                .build();

        return mapToDto(panelClinicoRepository.save(panel));
    }

    @Override
    public PanelClinicoResponseDto actualizar(Long id, PanelClinicoRequestDto request) {
        PanelClinico panel = obtenerPanelOLanzar(id);
        Long datasetId = panel.getDataset().getId();

        panelClinicoRepository.findByDatasetIdAndCodigoIgnoreCaseAndActivoTrue(datasetId, request.getCodigo())
                .filter(p -> !p.getId().equals(id))
                .ifPresent(p -> {
                    throw new IllegalArgumentException(
                            "Ya existe otro panel activo con el código: " + request.getCodigo());
                });

        panel.setCodigo(request.getCodigo());
        panel.setNombre(request.getNombre());
        panel.setDescripcion(request.getDescripcion());
        panel.setOrden(request.getOrden() != null ? request.getOrden() : 0);

        return mapToDto(panelClinicoRepository.save(panel));
    }

    @Override
    public void desactivar(Long id) {
        PanelClinico panel = obtenerPanelOLanzar(id);
        panel.setActivo(false);
        panelClinicoRepository.save(panel);
    }

    @Override
    public PanelEjecucionResponseDto ejecutar(Long id, EjecucionMetricaRequestDto request) {
        PanelClinico panel = obtenerPanelOLanzar(id);

        if (!Boolean.TRUE.equals(panel.getActivo())) {
            throw new IllegalArgumentException("El panel está desactivado.");
        }

        List<PanelMetricaResultadoDto> resultados = panelMetricaRepository.findByPanelIdAndActivaTrue(id)
                .stream()
                .filter(pm -> Boolean.TRUE.equals(pm.getMetrica().getActiva()))
                .sorted(Comparator.comparing(PanelMetrica::getOrden))
                .map(pm -> ejecutarPanelMetrica(pm, request))
                .toList();

        return PanelEjecucionResponseDto.builder()
                .panelId(panel.getId())
                .codigo(panel.getCodigo())
                .nombre(panel.getNombre())
                .datasetId(panel.getDataset().getId())
                .resultados(resultados)
                .build();
    }

    private PanelMetricaResultadoDto ejecutarPanelMetrica(
            PanelMetrica panelMetrica, EjecucionMetricaRequestDto request) {
        ResultadoMetricaResponseDto resultado =
                metricaClinicaService.ejecutar(panelMetrica.getMetrica().getId(), request);

        String titulo = panelMetrica.getTituloPersonalizado() != null
                ? panelMetrica.getTituloPersonalizado()
                : panelMetrica.getMetrica().getNombre();

        return PanelMetricaResultadoDto.builder()
                .panelMetricaId(panelMetrica.getId())
                .metricaId(panelMetrica.getMetrica().getId())
                .codigo(panelMetrica.getMetrica().getCodigo())
                .titulo(titulo)
                .tipoVisualizacion(panelMetrica.getTipoVisualizacion().name())
                .orden(panelMetrica.getOrden())
                .ancho(panelMetrica.getAncho())
                .resultado(resultado)
                .build();
    }

    private PanelClinico obtenerPanelOLanzar(Long id) {
        return panelClinicoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el panel con id: " + id));
    }

    private DatasetClinico obtenerDatasetOLanzar(Long datasetId) {
        return datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));
    }

    private DatasetClinico obtenerDatasetActivoOLanzar(Long datasetId) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);

        if (!Boolean.TRUE.equals(dataset.getActivo())) {
            throw new IllegalArgumentException("El dataset está desactivado.");
        }

        return dataset;
    }

    private PanelClinicoResponseDto mapToDto(PanelClinico panel) {
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
}
