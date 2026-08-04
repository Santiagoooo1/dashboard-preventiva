package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CategoriaPerfilDto {

    /** Valor técnico sin traducir: el frontend decide cómo mostrarlo. */
    private String valor;
    private long conteo;
    /** Porcentaje sobre valores VÁLIDOS (el denominador viaja en el padre). */
    private Double porcentaje;
}
