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

    /**
     * Fila TOTAL de la serie, calculada sobre TODOS los registros del rango de
     * una sola vez (Fase 6.9N).
     *
     * <p>No es el promedio de los puntos: un mes con 1 caso sobre 8 y otro con
     * 0 sobre 22 dan 3,33 % en conjunto, no 6,25 %. Promediar porcentajes
     * mensuales pondera igual un mes de 8 intervenciones y uno de 22, y en
     * vigilancia esa cifra se lee como la tasa del periodo.
     *
     * <p>Sale del mismo cálculo que cada punto, así que no puede discrepar de
     * ellos: {@code periodo} lleva la etiqueta del rango completo.
     */
    private PuntoSerieDto total;
}
