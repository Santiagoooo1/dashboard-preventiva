package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RellenarColumnaTrabajoRequestDto {

    private String nombreColumna;

    /** Opcional: si se indica, solo se rellenan filas cuyo error activo coincida con este tipo. */
    private String tipoError;

    private String valor;

    /** Por defecto true: solo filas con un error activo en esta columna (ver tipoError). */
    private Boolean soloFilasConEsteProblema;
}
