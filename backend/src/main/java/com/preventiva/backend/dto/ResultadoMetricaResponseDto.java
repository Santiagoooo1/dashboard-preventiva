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

    // --- Fase 6.9I.2 ---

    /**
     * OK o SIN_BASE_EVALUABLE. Cuando no es OK, {@code valor} es null y el
     * frontend debe explicar por qué en vez de pintar un cero.
     */
    private String estado;

    /**
     * Resultado no numérico: la fecha de un MINIMO/MAXIMO sobre campo FECHA, o
     * la etiqueta de una CATEGORIA_PRINCIPAL. {@code valor} sigue llevando la
     * parte numérica cuando existe (por ejemplo la frecuencia de la categoría).
     */
    private String valorTexto;

    /** Qué cuenta el numerador, en lenguaje clínico. */
    private String etiquetaNumerador;

    /** Sobre qué población se calcula. */
    private String etiquetaDenominador;
}
