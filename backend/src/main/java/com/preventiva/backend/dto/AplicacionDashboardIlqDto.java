package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Resultado de aplicar la plantilla de dashboard clínico (Fase 6.9I.4).
 *
 * <p>Distingue creado de actualizado para que la interfaz pueda decir la verdad
 * («se ha creado» frente a «ya estaba y se ha actualizado») en vez de un
 * mensaje genérico.
 */
@Getter
@Setter
@Builder
public class AplicacionDashboardIlqDto {

    private Long panelId;
    private String panelCodigo;
    private String panelNombre;

    /** false = el panel ya existía y se ha actualizado. */
    private Boolean panelCreado;

    private int metricasCreadas;
    private int metricasActualizadas;
    private int widgetsCreados;
    private int widgetsActualizados;

    /** Total tras aplicar: debe ser 14 y 14. */
    private int totalMetricas;
    private int totalWidgets;
}
