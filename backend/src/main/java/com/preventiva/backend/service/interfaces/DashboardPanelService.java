package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardPanelResponseDto;

public interface DashboardPanelService {

    DashboardPanelResponseDto obtenerDashboard(Long panelId, DashboardPanelRequestDto request);
}
