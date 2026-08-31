package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Sobre qué población deben calcularse los indicadores de actividad del
 * dashboard inicial (Fase 6.9L.2).
 *
 * <p>No es una métrica: es el suelo común de varias. El generador del dashboard
 * lo pide una vez y lo aplica a los indicadores que describen actividad.
 */
@Getter
@Setter
@Builder
public class BaseEvaluableDashboardDto {

    /**
     * true cuando el dataset tiene {@code fechaEvento} y, por tanto, sabemos
     * distinguir un evento real de una ficha a medio rellenar.
     */
    private boolean aplicaFechaEvento;

    /**
     * Filtros que acotan la población de actividad. Vacío cuando no se puede
     * acotar: entonces las métricas se calculan sobre todo, como siempre.
     */
    private List<FiltroMetricaDto> filtros;

    /**
     * Cómo llamar al indicador de volumen. «Intervenciones» cuando cuenta
     * eventos con fecha; «Total de registros» cuando cuenta filas sin más.
     */
    private String etiquetaVolumen;

    /** Por qué se acota (o por qué no), para poder auditarlo. */
    private String motivo;
}
