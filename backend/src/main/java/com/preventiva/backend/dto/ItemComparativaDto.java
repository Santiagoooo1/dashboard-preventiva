package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ItemComparativaDto {

    private String etiqueta;
    private Double valor;
    private Long totalNumerador;
    private Long totalDenominador;

    /** OK o SIN_BASE_EVALUABLE: un grupo sin base no es un cero (Fase 6.9I.2). */
    private String estado;
}
