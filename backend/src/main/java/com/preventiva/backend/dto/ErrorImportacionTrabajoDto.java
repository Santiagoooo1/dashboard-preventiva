package com.preventiva.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Error de una fila de la copia de trabajo. Misma estructura que
 * {@link ErrorFilaImportacionGenericaDto}, pero con constructores sin
 * argumentos para poder persistirse dentro de columnas JSONB.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorImportacionTrabajoDto {

    private Integer numeroFila;
    private String nombreColumna;
    private String valorOriginal;
    private String tipoError;
    private String severidad;
    private String mensaje;
}
