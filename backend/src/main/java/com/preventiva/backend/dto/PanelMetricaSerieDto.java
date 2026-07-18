package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PanelMetricaSerieDto {

    private Long panelMetricaId;
    private Long metricaId;
    private String codigo;
    private String titulo;
    private SerieTemporalResponseDto serie;
}
