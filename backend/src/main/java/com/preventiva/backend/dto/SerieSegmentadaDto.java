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
}
