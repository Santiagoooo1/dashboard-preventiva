package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ImportarDesdeTrabajoResponseDto {

    private ImportacionTrabajoResponseDto importacionTrabajo;
    private ImportacionGenericaResponseDto importacionGenerica;
    private Integer filasImportadas;
    private Integer filasExcluidas;
    private String resumen;
}
