package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ColumnaExcelDetectadaDto {

    private String nombreColumna;
    private Boolean reconocida;
    private String campoDestino;
    private String tipoDato;
}