package com.preventiva.backend.controller;

import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.PanelClinicoResponseDto;
import com.preventiva.backend.dto.PanelEjecucionResponseDto;
import com.preventiva.backend.dto.PanelMetricaRequestDto;
import com.preventiva.backend.dto.PanelMetricaResponseDto;
import com.preventiva.backend.service.interfaces.PanelClinicoService;
import com.preventiva.backend.service.interfaces.PanelMetricaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PanelClinicoController {

    private final PanelClinicoService panelClinicoService;
    private final PanelMetricaService panelMetricaService;

    @GetMapping("/api/datasets-clinicos/{datasetId}/paneles")
    public List<PanelClinicoResponseDto> listarPorDataset(@PathVariable("datasetId") Long datasetId) {
        return panelClinicoService.listarPorDataset(datasetId);
    }

    @PostMapping("/api/datasets-clinicos/{datasetId}/paneles")
    @ResponseStatus(HttpStatus.CREATED)
    public PanelClinicoResponseDto crear(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody PanelClinicoRequestDto request) {
        return panelClinicoService.crear(datasetId, request);
    }

    @GetMapping("/api/paneles-clinicos/{id}")
    public PanelClinicoResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return panelClinicoService.obtenerPorId(id);
    }

    @PutMapping("/api/paneles-clinicos/{id}")
    public PanelClinicoResponseDto actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody PanelClinicoRequestDto request) {
        return panelClinicoService.actualizar(id, request);
    }

    @DeleteMapping("/api/paneles-clinicos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable("id") Long id) {
        panelClinicoService.desactivar(id);
    }

    @PostMapping("/api/paneles-clinicos/{id}/ejecutar")
    public PanelEjecucionResponseDto ejecutar(
            @PathVariable("id") Long id,
            @RequestBody(required = false) EjecucionMetricaRequestDto request) {
        return panelClinicoService.ejecutar(id, request);
    }

    @GetMapping("/api/paneles-clinicos/{id}/metricas")
    public List<PanelMetricaResponseDto> listarMetricas(@PathVariable("id") Long id) {
        return panelMetricaService.listarPorPanel(id);
    }

    @PostMapping("/api/paneles-clinicos/{id}/metricas")
    @ResponseStatus(HttpStatus.CREATED)
    public PanelMetricaResponseDto crearMetrica(
            @PathVariable("id") Long id,
            @Valid @RequestBody PanelMetricaRequestDto request) {
        return panelMetricaService.crear(id, request);
    }

    @PutMapping("/api/paneles-clinicos/{id}/metricas/{panelMetricaId}")
    public PanelMetricaResponseDto actualizarMetrica(
            @PathVariable("id") Long id,
            @PathVariable("panelMetricaId") Long panelMetricaId,
            @Valid @RequestBody PanelMetricaRequestDto request) {
        return panelMetricaService.actualizar(id, panelMetricaId, request);
    }

    @DeleteMapping("/api/paneles-clinicos/{id}/metricas/{panelMetricaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarMetrica(
            @PathVariable("id") Long id,
            @PathVariable("panelMetricaId") Long panelMetricaId) {
        panelMetricaService.desactivar(id, panelMetricaId);
    }
}
