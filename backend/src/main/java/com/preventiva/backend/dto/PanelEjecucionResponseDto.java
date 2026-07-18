package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PanelEjecucionResponseDto {

    private Long panelId;
    private String codigo;
    private String nombre;
    private Long datasetId;
    private List<PanelMetricaResultadoDto> resultados;
}
