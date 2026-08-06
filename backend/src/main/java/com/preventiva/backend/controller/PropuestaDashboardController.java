package com.preventiva.backend.controller;

import com.preventiva.backend.dto.PropuestaDashboardResponseDto;
import com.preventiva.backend.service.interfaces.PropuestaDashboardService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PropuestaDashboardController {

    private final PropuestaDashboardService propuestaDashboardService;

    /**
     * Qué widgets propondría el dashboard recomendado. Solo lectura: la creación
     * la hace el frontend con los endpoints de métricas y widgets, sobre las
     * propuestas que el usuario haya dejado marcadas.
     */
    @GetMapping("/api/datasets-clinicos/{datasetId}/dashboard-recomendado/propuesta")
    public PropuestaDashboardResponseDto proponer(@PathVariable("datasetId") Long datasetId) {
        return propuestaDashboardService.proponer(datasetId);
    }
}
