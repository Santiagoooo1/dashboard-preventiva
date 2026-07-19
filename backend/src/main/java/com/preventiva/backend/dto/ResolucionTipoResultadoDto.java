package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResolucionTipoResultadoDto {

    private String tipoVisualizacion;
    private String tipoResultadoSiDistribucion;
    private String tipoResultadoConAgrupacion;
    private String tipoResultadoSinAgrupacion;
}
