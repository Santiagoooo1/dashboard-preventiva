package com.preventiva.backend.controller;

import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardPanelResponseDto;
import com.preventiva.backend.dto.PanelMetricaConfiguracionWidgetRequestDto;
import com.preventiva.backend.dto.PanelMetricaResponseDto;
import com.preventiva.backend.service.interfaces.DashboardPanelService;
import com.preventiva.backend.service.interfaces.PanelMetricaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardPanelController {

    private final DashboardPanelService dashboardPanelService;
    private final PanelMetricaService panelMetricaService;

    @PostMapping("/api/paneles-clinicos/{id}/dashboard")
    public DashboardPanelResponseDto obtenerDashboard(
            @PathVariable("id") Long id,
            @RequestBody(required = false) DashboardPanelRequestDto request) {
        return dashboardPanelService.obtenerDashboard(id, request);
    }

    @PutMapping("/api/paneles-clinicos/{panelId}/metricas/{panelMetricaId}/configuracion-widget")
    public PanelMetricaResponseDto actualizarConfiguracionWidget(
            @PathVariable("panelId") Long panelId,
            @PathVariable("panelMetricaId") Long panelMetricaId,
            @RequestBody PanelMetricaConfiguracionWidgetRequestDto request) {
        return panelMetricaService.actualizarConfiguracionWidget(panelId, panelMetricaId, request);
    }
}
