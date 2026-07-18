package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class DashboardPanelResponseDto {

    private DashboardPanelInfoDto panel;
    private DashboardDatasetInfoDto dataset;
    private DashboardFiltrosAplicadosDto filtrosAplicados;
    private List<DashboardWidgetDto> widgets;
    private DashboardPanelResumenDto resumen;
}
