package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class DeteccionColumnasResponseDto {

    private String nombreArchivo;
    private Integer totalColumnas;
    private List<ColumnaDetectadaResponseDto> columnas;
}
