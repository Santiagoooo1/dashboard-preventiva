package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ImportacionExcelResponseDto {

    private Long importacionId;

    private String nombreArchivo;

    private Long plantillaId;

    private String codigoPlantilla;

    private Integer filasLeidas;

    private Integer filasImportadas;

    private Integer filasConError;

    private Integer totalAdvertencias;

    private String estado;

    private String mensaje;
}
