package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardWidgetDto {

    private Long panelMetricaId;
    private Long metricaId;
    private String codigo;
    private String titulo;
    private String descripcion;
    private String tipoVisualizacion;
    private String tipoResultado;
    private Integer orden;
    private Integer ancho;
    private String estado;
    private ResultadoMetricaResponseDto resultadoActual;
    private SerieTemporalResponseDto serieTemporal;
    private ComparativaResponseDto comparativa;
    private DashboardWidgetErrorDto error;
}
