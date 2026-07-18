package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ResultadoMetricaResponseDto {

    private Long metricaId;
    private String codigo;
    private String nombre;
    private String tipoMetrica;
    private Double valor;
    private String unidad;
    private Long totalNumerador;
    private Long totalDenominador;
    private List<ItemDistribucionDto> items;
}
