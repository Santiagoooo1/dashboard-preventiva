package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ErrorImportacionExcelResponseDto {

    private Long id;
    private Integer numeroFila;
    private String nombreColumna;
    private String valorOriginal;
    private String tipoError;
    private String severidad;
    private String mensaje;
}
