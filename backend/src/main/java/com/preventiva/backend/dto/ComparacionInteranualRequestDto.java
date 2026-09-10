package com.preventiva.backend.dto;

import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.TipoComparacionInteranual;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Petición de una comparación entre años (Fase 6.9O).
 *
 * <p>El concepto se pide por su código canónico, no por el id de un campo: los
 * campos de 2024 y de 2026 son filas distintas de {@code campos_clinicos} y
 * pueden venir de cabeceras redactadas de otra forma. Lo que los hace
 * comparables es que signifiquen lo mismo.
 */
@Getter
@Setter
public class ComparacionInteranualRequestDto {

    /** Datasets a comparar. Uno solo también vale: se comparan sus años. */
    private List<Long> datasetIds;

    /** Código canónico del concepto clínico (ver CatalogoColumnasClinicas). */
    private String codigoCanonico;

    private TipoComparacionInteranual tipoComparacion;

    /** Detalle dentro de cada año. MES por defecto. */
    private Granularidad granularidad;

    /** Filtros a aplicar en todos los datasets; deben existir en todos. */
    private List<FiltroMetricaDto> filtros;

    /** Si se indica, solo estos años. Vacío = todos los que haya. */
    private List<Integer> anios;
}
