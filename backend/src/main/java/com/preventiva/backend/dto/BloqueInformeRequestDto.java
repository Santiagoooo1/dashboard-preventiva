package com.preventiva.backend.dto;

import com.preventiva.backend.enums.TipoBloqueInforme;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Alta o edición de un bloque. Lo que se envía es a qué apunta, nunca el
 * resultado: eso se recalcula en cada apertura.
 */
@Getter
@Setter
public class BloqueInformeRequestDto {

    @NotNull(message = "Indica qué tipo de bloque quieres añadir.")
    private TipoBloqueInforme tipoBloque;

    /** Columnas sobre 12. Por defecto ocupa la fila entera. */
    @Min(1)
    @Max(12)
    private Integer ancho;

    private Integer orden;

    /** Obligatoria en KPI, GRAFICA y TABLA. */
    private Long metricaId;

    private TipoVisualizacion tipoVisualizacion;
    private TipoResultadoWidget tipoResultadoWidget;
    private ConfiguracionWidgetDto configuracionWidget;

    /** Obligatoria en COMPARACION_INTERANUAL. */
    private ComparacionInteranualRequestDto configuracionComparacion;

    /** Obligatorio en TITULO, SUBTITULO y TEXTO. Texto plano, nunca HTML. */
    @Size(max = 4000)
    private String contenidoTexto;

    @Size(max = 200)
    private String tituloPersonalizado;
}
