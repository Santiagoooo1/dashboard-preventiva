package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Metadata de una columna: el frontend no adivina cómo formatear cada valor. */
@Getter
@Setter
@Builder
public class ColumnaSubconjuntoDto {

    private String codigo;
    private String etiqueta;
    private String tipoDato;
    private Integer orden;
    /** true en la columna del identificador individual: no puede ocultarse. */
    private boolean identificador;
}
