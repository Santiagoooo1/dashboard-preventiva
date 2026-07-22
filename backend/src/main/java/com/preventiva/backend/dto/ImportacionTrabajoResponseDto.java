package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ImportacionTrabajoResponseDto {

    private Long id;
    private Long datasetId;
    private Long plantillaId;
    private String nombreArchivoOriginal;
    private String origen;
    private Integer indiceHoja;
    private Integer filaCabecera;
    private String estado;
    private Integer totalFilasLeidas;
    private Integer totalFilasExcluidas;
    private Integer totalErrores;
    private Integer totalAdvertencias;
    private Boolean importable;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaRevalidacion;
}
