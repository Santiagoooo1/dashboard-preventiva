package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PanelMetricaResultadoDto {

    private Long panelMetricaId;
    private Long metricaId;
    private String codigo;
    private String titulo;
    private String tipoVisualizacion;
    private Integer orden;
    private Integer ancho;
    private ResultadoMetricaResponseDto resultado;
}
