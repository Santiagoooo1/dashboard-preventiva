package com.preventiva.backend.enums;

/**
 * Cuánta relevancia tiene una columna para los dashboards (Fase 6.9J.1).
 *
 * <p>Es un concepto INDEPENDIENTE de los otros dos atributos del campo, con los
 * que hasta ahora se confundía:
 *
 * <ul>
 *   <li>{@code obligatorio} — si el dato debe venir informado al importar. Es
 *       una regla de calidad del dato, no de análisis: un campo puede ser
 *       obligatorio y no interesar en ningún gráfico.</li>
 *   <li>{@code esComun} — si la columna pertenece al modelo clínico común
 *       (paciente, fecha, procedimiento…). Es una propiedad estructural del
 *       esquema, no una opinión sobre su valor analítico.</li>
 *   <li>{@code prioridadDashboard} — esto: cuánto merece la pena proponerla en
 *       un dashboard. Lo decide quien conoce el uso clínico del dataset.</li>
 * </ul>
 *
 * <p>Antes se aproximaba «esComun ⇒ fundamental» y «obligatorio ⇒ importante»,
 * que es falso en ambos sentidos: hay campos comunes irrelevantes para un
 * servicio concreto y campos opcionales que son el indicador principal.
 */
public enum PrioridadDashboardCampo {

    /** Especialmente relevante para indicadores, filtros o gráficos. */
    FUNDAMENTAL,

    /** Útil, conviene considerarlo en el análisis. */
    IMPORTANTE,

    /** Disponible para análisis, sin prioridad especial. Valor por defecto. */
    NORMAL,

    /**
     * No se propondrá automáticamente en dashboards.
     *
     * <p>Nunca se deduce sola: excluir una columna del análisis es una decisión
     * clínica, y adivinarla escondería datos sin que nadie lo hubiera pedido.
     * El usuario sigue pudiendo usarla a mano en cualquier métrica.
     */
    EXCLUIR
}
