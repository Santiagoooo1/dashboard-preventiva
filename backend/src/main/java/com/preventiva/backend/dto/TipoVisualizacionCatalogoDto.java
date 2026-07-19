package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TipoVisualizacionCatalogoDto {

    private String codigo;
    private String nombre;
    private String tipoResultadoPorDefecto;
}
