package com.preventiva.backend.enums;

/**
 * Operaciones que sabe ejecutar el motor de métricas.
 *
 * <p>Las cinco primeras existían desde la Fase 5; las seis últimas son las
 * capacidades genéricas que añade la Fase 6.9I.2 para que cualquier columna
 * importada pueda producir al menos una métrica válida.
 *
 * <p>Deliberadamente NO hay operaciones de dominio (ILQ, profilaxis, Drago):
 * todas son genéricas y se especializan mediante la configuración.
 *
 * <p>Tampoco hay un {@code PORCENTAJE_CONDICIONAL}: sería un duplicado de
 * {@link #PORCENTAJE}, que ya calcula numerador/denominador con filtros
 * propios. Lo que faltaba —filtros base comunes y etiquetas explícitas— se
 * añadió a su configuración en vez de crear un segundo tipo equivalente.
 */
public enum TipoMetrica {
    CONTEO,
    PORCENTAJE,
    PROMEDIO,
    SUMA,
    DISTRIBUCION,

    /** Cuántos valores distintos y no nulos tiene un campo (pacientes únicos, etc.). */
    CONTEO_DISTINTO,

    /** Porcentaje de registros con el campo informado sobre el total evaluado. */
    COMPLETITUD,

    /** Percentil 50 de un campo numérico: resistente a valores extremos, a diferencia de la media. */
    MEDIANA,

    /** Valor más bajo. Numérico o fecha (en fecha equivale a "primera fecha"). */
    MINIMO,

    /** Valor más alto. Numérico o fecha (en fecha equivale a "última fecha"). */
    MAXIMO,

    /** Categoría más frecuente de un campo, con su frecuencia sobre el total. */
    CATEGORIA_PRINCIPAL
}
