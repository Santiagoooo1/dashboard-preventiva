package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PanelMetricaResponseDto {

    private Long id;
    private Long panelId;
    private Long metricaId;
    private String metricaCodigo;
    private String metricaNombre;
    private String tituloPersonalizado;
    private String descripcionPersonalizada;
    private String tipoVisualizacion;
    private Integer orden;
    private Integer ancho;
    private Boolean activa;
}
