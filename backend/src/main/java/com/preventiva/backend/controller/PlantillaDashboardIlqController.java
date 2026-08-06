package com.preventiva.backend.controller;

import com.preventiva.backend.dto.AplicacionDashboardIlqDto;
import com.preventiva.backend.dto.CompatibilidadDashboardIlqDto;
import com.preventiva.backend.service.interfaces.PlantillaDashboardIlqService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlantillaDashboardIlqController {

    private final PlantillaDashboardIlqService plantillaDashboardIlqService;

    /** Consulta previa: qué campos hay, cuáles faltan y si ya está aplicado. */
    @GetMapping("/api/datasets-clinicos/{datasetId}/dashboard-ilq/compatibilidad")
    public CompatibilidadDashboardIlqDto comprobarCompatibilidad(@PathVariable("datasetId") Long datasetId) {
        return plantillaDashboardIlqService.comprobarCompatibilidad(datasetId);
    }

    /**
     * Aplica la plantilla. Requiere confirmación explícita del usuario: no se
     * invoca sola tras una importación.
     */
    @PostMapping("/api/datasets-clinicos/{datasetId}/dashboard-ilq")
    public AplicacionDashboardIlqDto aplicar(@PathVariable("datasetId") Long datasetId) {
        return plantillaDashboardIlqService.aplicar(datasetId);
    }
}
