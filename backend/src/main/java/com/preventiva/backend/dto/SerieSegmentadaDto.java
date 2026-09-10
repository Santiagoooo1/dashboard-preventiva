package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class SerieSegmentadaDto {

    private String etiqueta;
    private List<PuntoSerieDto> puntos;

    /** Fila TOTAL de este segmento, acumulada sobre todo el rango (Fase 6.9N). */
    private PuntoSerieDto total;
}
