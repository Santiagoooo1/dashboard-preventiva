package com.preventiva.backend.enums;

/**
 * Cómo se interpreta analíticamente una columna importada (Fase 6.9I.2).
 *
 * <p>El {@link TipoDatoExcel} dice cómo se almacena el valor; el rol dice qué
 * tiene sentido preguntarle. Dos columnas TEXTO pueden ser una categoría con
 * ocho valores y un identificador de paciente con doscientos veinte: la misma
 * operación (una distribución en donut) es útil en la primera e inútil en la
 * segunda, así que la oferta de operaciones se decide por rol, no por tipo.
 */
public enum RolAnaliticoCampo {

    /** Identifica a la persona o entidad: se cuenta en distintos, nunca se promedia. */
    IDENTIFICADOR,

    /** Sí / No / Sin dato. */
    BOOLEANO,

    /** Conjunto acotado de valores repetidos: distribuciones y comparativas. */
    CATEGORICO,

    /** Magnitud sobre la que tienen sentido media, mediana, suma, mínimo y máximo. */
    NUMERICO,

    /** Fecha: series temporales, primera y última. */
    FECHA,

    /** Texto con demasiados valores distintos para ser una categoría útil. */
    TEXTO_LIBRE
}
