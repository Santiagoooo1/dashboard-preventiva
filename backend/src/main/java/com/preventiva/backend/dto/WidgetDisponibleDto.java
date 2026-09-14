package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Un widget que ya existe en un dashboard y puede insertarse en un informe
 * (Fase 6.9Q.1).
 *
 * <p>Lleva la CONFIGURACIÓN, no el resultado: es lo que hace falta para que el
 * bloque del informe se vea exactamente igual que en su dashboard de origen.
 * Una «Evolución mensual de ILQ» que allí es LINEAS + SERIE_TEMPORAL + MES debe
 * seguir siéndolo en el informe, no convertirse en unas barras genéricas.
 */
@Getter
@Setter
@Builder
public class WidgetDisponibleDto {

    private Long panelMetricaId;
    private Long metricaId;

    /** Lo que el usuario lee en su dashboard: título propio o nombre de la métrica. */
    private String titulo;

    /**
     * Solo si el widget tiene título propio en el panel.
     *
     * <p>Se separa de {@link #titulo} a propósito: el bloque del informe debe
     * copiar el título personalizado, pero NO congelar el nombre de la métrica.
     * Si mañana se renombra el indicador, el informe debe seguirlo.
     */
    private String tituloPersonalizado;

    private String tipoVisualizacion;
    private String tipoResultadoWidget;
    private ConfiguracionWidgetDto configuracionWidget;
    private Integer ancho;
}
