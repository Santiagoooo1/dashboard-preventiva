package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.MetricaClinicaResponseDto;
import com.preventiva.backend.dto.PreviewMetricaRequestDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;

import java.util.List;

public interface MetricaClinicaService {

    List<MetricaClinicaResponseDto> listarPorDataset(Long datasetId);

    MetricaClinicaResponseDto obtenerPorId(Long id);

    MetricaClinicaResponseDto crear(Long datasetId, MetricaClinicaRequestDto request);

    MetricaClinicaResponseDto actualizar(Long id, MetricaClinicaRequestDto request);

    void desactivar(Long id);

    ResultadoMetricaResponseDto ejecutar(Long id, EjecucionMetricaRequestDto request);

    ResultadoMetricaResponseDto preview(Long datasetId, PreviewMetricaRequestDto request);
}
