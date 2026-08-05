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

    /** OK o SIN_BASE_EVALUABLE: un periodo sin base no es un cero (Fase 6.9I.2). */
    private String estado;
}
