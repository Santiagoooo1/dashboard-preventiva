package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class SerieTemporalResponseDto {

    private Long metricaId;
    private String codigo;
    private String tipoMetrica;
    private String granularidad;
    private String segmentadoPor;
    private List<PuntoSerieDto> puntos;
    private List<SerieSegmentadaDto> series;
}
