package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OpcionCatalogoDto {

    private String codigo;
    private String nombre;
    private String descripcion;
}
