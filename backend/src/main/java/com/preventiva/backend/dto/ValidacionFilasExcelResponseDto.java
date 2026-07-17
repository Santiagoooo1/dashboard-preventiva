package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ValidacionFilasExcelResponseDto {

    private String nombreArchivo;

    private Long plantillaId;

    private String codigoPlantilla;

    private Integer indiceHoja;

    private Integer filaCabecera;

    private Integer totalFilasLeidas;

    private Integer filasValidas;

    private Integer filasConError;

    private Integer filasConAdvertencia;

    private List<ErrorFilaExcelDto> errores;

    private List<ErrorFilaExcelDto> erroresBloqueantes;

    private List<ErrorFilaExcelDto> advertencias;

    private Integer totalAdvertencias;

    private Boolean valida;

    private Boolean importable;

    private String resumen;
}