package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Algo que el usuario debe saber antes de leer la comparación. */
@Getter
@Setter
@Builder
public class AdvertenciaComparacionDto {

    /** PERIODOS_SOLAPADOS, DATASET_MULTIANUAL… */
    private String codigo;

    /** Explicación en el idioma del usuario, lista para mostrar. */
    private String mensaje;
}
