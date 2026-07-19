package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class WidgetMetadataDto {

    private Long panelMetricaId;
    private Long metricaId;
    private String codigo;
    private String nombre;
    private String tipoMetrica;
    private String tipoVisualizacion;
    private String tipoResultadoWidgetConfigurado;
    private String tipoResultadoActual;
    private List<String> tipoResultadosPermitidos;
    private ConfiguracionWidgetDto configuracionWidgetActual;
}
