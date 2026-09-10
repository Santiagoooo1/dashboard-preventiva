package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ColumnaDetectadaResponseDto {

    private Integer indiceColumna;
    private String nombreOriginal;
    private String nombreNormalizado;

    /**
     * Campo clínico que es esta columna, cuando la aplicación la reconoce por
     * su nombre. Null si no está en el catálogo.
     *
     * <p>Cuando viene relleno, manda sobre la heurística del asistente: si
     * sabemos qué es la columna, no hay nada que adivinar.
     */
    private String codigoCanonico;

    /** Tipo que corresponde al campo canónico. Null si la columna no se reconoce. */
    private String tipoDatoCanonico;

    /** Si el campo canónico es de los comunes a todos los datasets. */
    private Boolean esComunCanonico;

    /** Nombre recomendado para mostrar cuando el del archivo es críptico («ILQ»). */
    private String etiquetaCanonica;

    /**
     * Por qué se reconoció: CANONICA_EXACTA o ALIAS. Sirve para auditar la
     * decisión; el asistente no necesita enseñárselo al médico.
     */
    private String origenReconocimiento;
}
