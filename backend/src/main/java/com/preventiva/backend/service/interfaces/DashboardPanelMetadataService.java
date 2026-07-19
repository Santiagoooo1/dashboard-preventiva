package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DashboardPanelMetadataResponseDto;

public interface DashboardPanelMetadataService {

    DashboardPanelMetadataResponseDto obtenerMetadataPanel(Long panelId);
}
