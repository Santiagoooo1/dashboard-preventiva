package com.preventiva.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.preventiva.backend.enums.TratamientoNulos;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Configuración persistida de una métrica (columna jsonb {@code configuracion}).
 *
 * <p>Es la única estructura que interpreta el ejecutor: los tres modos de
 * creación del constructor (desde columna, porcentaje condicional y avanzada)
 * terminan produciendo este mismo objeto. No hay configuraciones paralelas.
 *
 * <p>Los campos añadidos en la Fase 6.9I.2 son opcionales y se omiten al
 * serializar cuando son nulos, así que las métricas guardadas antes siguen
 * leyéndose y comportándose igual.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfiguracionMetricaDto {

    /**
     * Filtros base. Acotan la población ANTES de cualquier otro cálculo.
     *
     * <p>En PORCENTAJE se aplican también al numerador y al denominador, que es
     * lo que convierte un porcentaje corriente en uno condicional: «entre los
     * casos con profilaxis indicada, qué proporción fue adecuada».
     */
    private List<FiltroMetricaDto> filtros;

    private FiltroGrupoDto numerador;
    private FiltroGrupoDto denominador;

    /** Campo sobre el que opera la métrica (media, mediana, suma, distintos, completitud, mínimo, máximo). */
    private String campoValor;

    /** Campo por el que se agrupa (distribución, categoría principal). */
    private String campoAgrupacion;

    // --- Fase 6.9I.2 ---

    /** Qué hacer con los registros sin valor. Por defecto EXCLUIR. */
    private TratamientoNulos tratamientoNulos;

    /** Qué cuenta el numerador, en lenguaje clínico. Se muestra junto al resultado. */
    private String etiquetaNumerador;

    /** Sobre qué población se calcula. Se muestra junto al resultado. */
    private String etiquetaDenominador;

    /**
     * Cuántas categorías conserva una distribución antes de agrupar el resto en
     * «Otros». Nulo = sin recorte. «Otros» es presentación: nunca un valor
     * técnico sobre el que se pueda filtrar.
     */
    private Integer maxCategorias;
}
