package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Una línea de la comparación: un año de un dataset (Fase 6.9O).
 *
 * <p>La unidad es el par dataset-año y no el dataset a secas, porque un fichero
 * puede traer tres años dentro. Así el mismo modelo sirve tanto si cada año está
 * en su propio dataset como si todos están juntos.
 */
@Getter
@Setter
@Builder
public class SerieAnualDto {

    private Long datasetId;
    private String datasetCodigo;
    private String datasetNombre;

    /** Año que representa esta serie, deducido de las fechas reales. */
    private Integer anio;

    /** Lo que se muestra en la cabecera de la columna. */
    private String etiqueta;

    /** Una celda por periodo del año, en orden cronológico. */
    private List<CeldaComparacionDto> celdas;

    /**
     * Total del año, recalculado desde sus numeradores y denominadores.
     * Nunca el promedio de los porcentajes de sus periodos.
     */
    private CeldaComparacionDto total;

    /**
     * Variación frente a la serie inmediatamente anterior, si la hay.
     *
     * <p>En porcentajes va en PUNTOS PORCENTUALES: de 3,1 % a 1,8 % son −1,3 pp.
     * Decir «−41,9 %» responde a otra pregunta y se presta a leerlo como si la
     * tasa hubiera bajado al 41,9 %.
     */
    private List<CeldaComparacionDto> variacion;
    private CeldaComparacionDto variacionTotal;

    /** «pp» para porcentajes, «absoluta» para recuentos y numéricos. */
    private String unidadVariacion;
}
