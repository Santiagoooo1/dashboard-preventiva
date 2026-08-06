package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Un widget que el sistema propone para el dashboard recomendado
 * (Fase 6.9I.4.1). Es una propuesta: el usuario la ve, puede desmarcarla y solo
 * entonces se crea.
 */
@Getter
@Setter
@Builder
public class PropuestaWidgetDto {

    /** Código estable de la métrica que se crearía. */
    private String codigoMetrica;
    private String nombre;
    private String descripcion;

    /** Valor de TipoMetrica. */
    private String tipoMetrica;
    private ConfiguracionMetricaDto configuracion;
    private String unidad;
    private Integer decimales;

    /** Columna de la que sale, para que el usuario reconozca la propuesta. */
    private String campoOrigen;
    private String campoOrigenEtiqueta;

    /** Por qué se propone: «Campo fundamental», «Campo obligatorio»… */
    private String motivo;

    /** Puntuación con la que se ordenó. Se expone para poder auditarla. */
    private int prioridad;

    // --- Widget ---
    private String tipoVisualizacion;
    private Integer ancho;
    private Integer orden;
    private String tipoResultado;
    private ConfiguracionWidgetDto configuracionWidget;

    /** Aviso a mostrar junto a la propuesta (alta cardinalidad, campo casi vacío…). */
    private String advertencia;
}
