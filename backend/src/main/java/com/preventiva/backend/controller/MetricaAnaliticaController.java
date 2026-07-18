package com.preventiva.backend.controller;

import com.preventiva.backend.dto.ComparativaRequestDto;
import com.preventiva.backend.dto.ComparativaResponseDto;
import com.preventiva.backend.dto.PanelSerieTemporalResponseDto;
import com.preventiva.backend.dto.SerieTemporalRequestDto;
import com.preventiva.backend.dto.SerieTemporalResponseDto;
import com.preventiva.backend.service.interfaces.MetricaAnaliticaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MetricaAnaliticaController {

    private final MetricaAnaliticaService metricaAnaliticaService;

    @PostMapping("/api/metricas-clinicas/{id}/serie-temporal")
    public SerieTemporalResponseDto serieTemporal(
            @PathVariable("id") Long id,
            @Valid @RequestBody SerieTemporalRequestDto request) {
        return metricaAnaliticaService.serieTemporal(id, request);
    }

    @PostMapping("/api/metricas-clinicas/{id}/comparativa")
    public ComparativaResponseDto comparativa(
            @PathVariable("id") Long id,
            @Valid @RequestBody ComparativaRequestDto request) {
        return metricaAnaliticaService.comparativa(id, request);
    }

    @PostMapping("/api/paneles-clinicos/{id}/serie-temporal")
    public PanelSerieTemporalResponseDto serieTemporalPanel(
            @PathVariable("id") Long id,
            @Valid @RequestBody SerieTemporalRequestDto request) {
        return metricaAnaliticaService.serieTemporalPanel(id, request);
    }
}
