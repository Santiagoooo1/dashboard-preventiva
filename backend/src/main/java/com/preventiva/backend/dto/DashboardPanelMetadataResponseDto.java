package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class DashboardPanelMetadataResponseDto {

    private PanelClinicoResponseDto panel;
    private DatasetClinicoResponseDto dataset;
    private List<String> camposFechaPermitidos;
    private List<String> camposAgrupacionPermitidos;
    private List<WidgetMetadataDto> widgets;
}
