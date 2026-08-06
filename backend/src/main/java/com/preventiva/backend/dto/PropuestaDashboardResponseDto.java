package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PropuestaDashboardResponseDto {

    private Long datasetId;
    private String datasetCodigo;

    /** Hay columnas suficientes para componer algo con sentido. */
    private Boolean suficiente;

    /** Motivo cuando no lo hay, en lenguaje de usuario. */
    private String motivoInsuficiente;

    /** Propuestas ya ordenadas por prioridad y acotadas al máximo. */
    private List<PropuestaWidgetDto> propuestas;

    /** Cuántos widgets como mucho compone el dashboard recomendado. */
    private int maximoWidgets;

    /** El dataset admite además la plantilla clínica de ILQ. */
    private Boolean compatibleIlq;
}
