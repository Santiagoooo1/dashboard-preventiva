package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ValidacionFilasImportacionGenericaResponseDto {

    private String nombreArchivo;
    private Long plantillaId;
    private Long datasetId;
    private Integer indiceHoja;
    private Integer filaCabecera;
    private Integer totalFilasLeidas;
    private Integer filasValidas;
    private Integer filasConError;
    private Integer filasConAdvertencia;
    private List<ErrorFilaImportacionGenericaDto> errores;
    private List<ErrorFilaImportacionGenericaDto> erroresBloqueantes;
    private List<ErrorFilaImportacionGenericaDto> advertencias;
    private Integer totalAdvertencias;
    private Boolean valida;
    private Boolean importable;
    private String resumen;
}
