package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ValidacionImportacionGenericaResponseDto {

    private String nombreArchivo;
    private Long plantillaId;
    private Long datasetId;
    private Integer totalColumnasDetectadas;
    private Integer totalColumnasReconocidas;
    private Integer totalColumnasNoReconocidas;
    private List<ColumnaMapeadaDto> columnasDetectadas;
    private List<String> columnasNoReconocidas;
    private List<String> camposObligatoriosFaltantes;
    private Boolean valida;
    private Boolean importable;
    private List<String> advertencias;
    private String resumen;
}
