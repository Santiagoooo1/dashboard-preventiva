package com.preventiva.backend.dto;

import com.preventiva.backend.enums.TipoVisualizacion;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PanelMetricaRequestDto {

    @NotNull
    private Long metricaId;

    private String tituloPersonalizado;

    private String descripcionPersonalizada;

    private TipoVisualizacion tipoVisualizacion;

    private Integer orden;

    private Integer ancho;
}
