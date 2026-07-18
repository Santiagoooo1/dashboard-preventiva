package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class PuntoSerieDto {

    private String periodo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Double valor;
    private Long totalNumerador;
    private Long totalDenominador;
}
