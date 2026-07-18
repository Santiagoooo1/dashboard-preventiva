package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MapeoColumnaExcelResponseDto {

    private Long id;
    private Long plantillaId;
    private String nombreColumnaExcel;
    private String campoDestino;
    private String tipoDato;
    private Boolean obligatoria;
    private String politicaCampoFaltante;
    private Boolean activa;
}
