package com.preventiva.backend.controller;

import com.preventiva.backend.dto.DashboardPanelMetadataResponseDto;
import com.preventiva.backend.service.interfaces.DashboardPanelMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardPanelMetadataController {

    private final DashboardPanelMetadataService dashboardPanelMetadataService;

    @GetMapping("/api/paneles-clinicos/{panelId}/dashboard-metadata")
    public DashboardPanelMetadataResponseDto obtenerMetadataPanel(@PathVariable("panelId") Long panelId) {
        return dashboardPanelMetadataService.obtenerMetadataPanel(panelId);
    }
}
