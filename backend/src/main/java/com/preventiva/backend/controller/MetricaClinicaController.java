package com.preventiva.backend.controller;

import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.MetricaClinicaResponseDto;
import com.preventiva.backend.dto.PreviewMetricaRequestDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MetricaClinicaController {

    private final MetricaClinicaService metricaClinicaService;

    @GetMapping("/api/datasets-clinicos/{datasetId}/metricas")
    public List<MetricaClinicaResponseDto> listarPorDataset(@PathVariable("datasetId") Long datasetId) {
        return metricaClinicaService.listarPorDataset(datasetId);
    }

    @PostMapping("/api/datasets-clinicos/{datasetId}/metricas")
    @ResponseStatus(HttpStatus.CREATED)
    public MetricaClinicaResponseDto crear(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody MetricaClinicaRequestDto request) {
        return metricaClinicaService.crear(datasetId, request);
    }

    @PostMapping("/api/datasets-clinicos/{datasetId}/metricas/preview")
    public ResultadoMetricaResponseDto preview(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody PreviewMetricaRequestDto request) {
        return metricaClinicaService.preview(datasetId, request);
    }

    @GetMapping("/api/metricas-clinicas/{id}")
    public MetricaClinicaResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return metricaClinicaService.obtenerPorId(id);
    }

    @PutMapping("/api/metricas-clinicas/{id}")
    public MetricaClinicaResponseDto actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody MetricaClinicaRequestDto request) {
        return metricaClinicaService.actualizar(id, request);
    }

    @DeleteMapping("/api/metricas-clinicas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable("id") Long id) {
        metricaClinicaService.desactivar(id);
    }

    @PostMapping("/api/metricas-clinicas/{id}/ejecutar")
    public ResultadoMetricaResponseDto ejecutar(
            @PathVariable("id") Long id,
            @RequestBody(required = false) EjecucionMetricaRequestDto request) {
        return metricaClinicaService.ejecutar(id, request);
    }
}
