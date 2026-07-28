package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NormalizarColumnaTrabajoRequestDto {

    private String nombreColumna;

    /** FECHA | BOOLEANO | NUMERO | TEXTO_TRIM */
    private String estrategia;
}
