package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * La columna viaja en el body (no en la ruta) para admitir nombres con "/",
 * espacios o acentos, como "Diagnóstico/CIE-10".
 */
@Getter
@Setter
public class CorregirCeldaTrabajoRequestDto {

    private String columna;
    private String valor;
}
