package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CrearImportacionTrabajoResponseDto {

    private ImportacionTrabajoResponseDto importacionTrabajo;
    private String resumen;
}
