package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ValidacionExcelResponseDto {

    private String nombreArchivo;
    private Long plantillaId;
    private String codigoPlantilla;

    private Integer totalColumnasDetectadas;
    private Integer totalColumnasReconocidas;
    private Integer totalColumnasNoReconocidas;

    private List<ColumnaExcelDetectadaDto> columnasDetectadas;
    private List<String> columnasNoReconocidas;
    private List<String> columnasObligatoriasFaltantes;

    private Boolean valida;

    private Boolean importable;
    private List<String> advertencias;
    private String resumen;
}