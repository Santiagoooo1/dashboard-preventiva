package com.preventiva.backend.enums;

/**
 * Por qué un resultado no trae valor (Fase 6.9I.2).
 *
 * <p>Antes, un porcentaje sin denominador devolvía {@code 0.0}: matemáticamente
 * indefinido, pero en pantalla indistinguible de un 0 % real. Sobre datos
 * clínicos eso es peligroso —"0 % de profilaxis adecuada" y "ningún caso en el
 * que evaluarla" son cosas muy distintas—, así que ahora el valor viaja como
 * {@code null} acompañado del motivo.
 */
public enum EstadoResultadoMetrica {

    /** Hay base suficiente y el valor es válido (incluido un 0 legítimo). */
    OK,

    /**
     * No hay registros sobre los que calcular: denominador cero, o ningún valor
     * informado para una media, mediana, mínimo o máximo. Nunca NaN ni Infinity.
     */
    SIN_BASE_EVALUABLE
}
