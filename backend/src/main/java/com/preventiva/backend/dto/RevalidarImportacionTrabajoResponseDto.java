package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RevalidarImportacionTrabajoResponseDto {

    private ImportacionTrabajoResponseDto importacionTrabajo;
    private String resumen;
}
