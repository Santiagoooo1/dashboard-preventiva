package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Comparación de un mismo concepto clínico entre varios años (Fase 6.9O).
 *
 * <p>Una sola estructura alimenta las tres vistas: la matriz interanual, la
 * tabla por año y la gráfica de una línea por año. Si cada una calculara lo
 * suyo, tarde o temprano dirían cosas distintas del mismo dato.
 */
@Getter
@Setter
@Builder
public class ComparacionInteranualResponseDto {

    private String codigoCanonico;
    /** Nombre legible del concepto, tomado de la etiqueta del campo. */
    private String etiquetaConcepto;
    private String tipoComparacion;
    private String granularidad;

    /**
     * Periodos del eje, iguales para todas las series: «01».. «12». Sin año,
     * porque el año es la columna.
     */
    private List<String> periodos;

    /** Una por año comparado, en orden cronológico. */
    private List<SerieAnualDto> series;

    /**
     * Unión de las categorías vistas en cualquier año (solo DISTRIBUCION).
     *
     * <p>Se conserva aunque una categoría no exista en algún año: quitarla
     * escondería precisamente la novedad.
     */
    private List<String> categorias;

    /** false cuando los datasets no son comparables: entonces no hay series. */
    private boolean comparable;

    /** Por qué no se puede comparar, en términos que el usuario entienda. */
    private String motivoNoComparable;

    private List<AdvertenciaComparacionDto> advertencias;
}
