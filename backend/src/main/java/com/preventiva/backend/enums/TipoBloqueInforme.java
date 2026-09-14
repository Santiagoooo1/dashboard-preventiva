package com.preventiva.backend.enums;

/**
 * Qué puede colocarse en una página de informe (Fase 6.9Q).
 *
 * <p>Deliberadamente genéricos: no hay KPI_ILQ ni TABLA_ADECUACION. Lo clínico
 * viene de la métrica a la que apunta el bloque, no del tipo de bloque.
 */
public enum TipoBloqueInforme {

    /** Una cifra con su unidad. Referencia una métrica. */
    KPI,

    /** Un gráfico. Referencia una métrica y su visualización. */
    GRAFICA,

    /** Una tabla, incluida la temporal. Referencia una métrica. */
    TABLA,

    /** Comparación entre años; lleva su propia configuración, no una métrica. */
    COMPARACION_INTERANUAL,

    TITULO,
    SUBTITULO,

    /** Texto plano multilínea: introducción, interpretación, conclusiones. */
    TEXTO,

    SEPARADOR,

    /** Fuerza el corte de página al imprimir. */
    SALTO_PAGINA
}
