package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class CatalogoFrontendResponseDto {

    private List<TipoMetricaCatalogoDto> tipoMetricas;
    private List<OperadorFiltroCatalogoDto> operadoresFiltro;
    private List<TipoVisualizacionCatalogoDto> tipoVisualizaciones;
    private List<OpcionCatalogoDto> tipoResultadoWidget;
    private List<OpcionCatalogoDto> granularidades;
    private List<OpcionCatalogoDto> tiposDato;
    private List<OpcionCatalogoDto> politicasCampoFaltante;
    private ReglasCompatibilidadDto reglasCompatibilidad;
}
