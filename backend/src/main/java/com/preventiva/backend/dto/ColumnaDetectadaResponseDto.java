package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ColumnaDetectadaResponseDto {

    private Integer indiceColumna;
    private String nombreOriginal;
    private String nombreNormalizado;
}
