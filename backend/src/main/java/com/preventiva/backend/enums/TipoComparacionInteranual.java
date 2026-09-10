package com.preventiva.backend.enums;

/**
 * Qué se compara entre años (Fase 6.9O).
 *
 * <p>No son tipos de métrica nuevos: cada uno se traduce a una configuración de
 * las que ya sabe calcular el motor. Existen para que quien pide la comparación
 * no tenga que redactar numeradores y denominadores a mano y arriesgarse a
 * escribirlos distinto en cada año.
 */
public enum TipoComparacionInteranual {

    /** Porcentaje de casos sobre los registros con el dato documentado. */
    TASA,

    /**
     * Número absoluto de casos positivos.
     *
     * <p>Responde a «cuántas infecciones hubo», que no es lo mismo que la tasa:
     * un año con menos intervenciones puede tener menos casos y peor tasa. Ni un
     * NULL ni un {@code false} cuentan como caso.
     */
    RECUENTO,

    /** Reparto por categorías, con la unión de las que aparecen en cualquier año. */
    DISTRIBUCION,

    /** N, media, mínimo y máximo de un campo numérico. */
    RESUMEN_NUMERICO
}
