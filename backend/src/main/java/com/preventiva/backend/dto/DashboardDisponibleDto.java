package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Un dashboard con los widgets que se pueden llevar a un informe. */
@Getter
@Setter
@Builder
public class DashboardDisponibleDto {

    private Long panelId;
    private String panelNombre;
    private Long datasetId;
    private String datasetNombre;
    private List<WidgetDisponibleDto> widgets;
}
