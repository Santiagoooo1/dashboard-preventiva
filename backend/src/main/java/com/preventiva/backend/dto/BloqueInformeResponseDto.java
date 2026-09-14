package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Un bloque tal como lo recibe el editor o la vista previa.
 *
 * <p>Los bloques analíticos llegan con su resultado ya calculado en
 * {@code widget} o {@code comparacion}, usando el mismo motor que el dashboard.
 * Si la referencia se ha borrado, llegan con {@code disponible = false} y un
 * motivo legible en vez de reventar la página entera.
 */
@Getter
@Setter
@Builder
public class BloqueInformeResponseDto {

    private Long id;
    private String tipoBloque;
    private Integer orden;
    private Integer ancho;

    private Long metricaId;
    private String tipoVisualizacion;
    private String tipoResultadoWidget;
    private ConfiguracionWidgetDto configuracionWidget;
    private ComparacionInteranualRequestDto configuracionComparacion;
    private String contenidoTexto;
    private String tituloPersonalizado;

    /** Nombre del dataset del que sale el dato, para la trazabilidad del pie. */
    private Long datasetId;
    private String datasetNombre;

    /** Resultado ya calculado de un KPI, gráfica o tabla. */
    private DashboardWidgetDto widget;

    /** Resultado ya calculado de una comparación entre años. */
    private ComparacionInteranualResponseDto comparacion;

    /** false cuando lo que referenciaba ya no existe. */
    private boolean disponible;

    /** «La métrica de este bloque ya no existe.» */
    private String motivoNoDisponible;
}
