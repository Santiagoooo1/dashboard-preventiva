package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ResumenTrazabilidadImportacionTrabajoDto {

    private Long importacionTrabajoId;
    private Long importacionGenericaId;
    private Long datasetId;
    private Long plantillaId;
    private String nombreArchivoOriginal;
    private String hashArchivoOriginal;
    private String estado;
    private Integer totalFilasLeidas;
    private Integer totalFilasExcluidas;
    private Integer totalErrores;
    private Integer totalAdvertencias;
    private Boolean importable;
    private Integer totalEventos;
    private Integer totalCorreccionesManuales;
    private Integer totalCorreccionesEnBloque;
    private Integer totalNormalizaciones;
    private Integer totalExclusiones;
    private Integer totalRestauraciones;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaRevalidacion;
}
