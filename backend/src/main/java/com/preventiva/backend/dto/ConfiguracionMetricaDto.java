package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ConfiguracionMetricaDto {

    private List<FiltroMetricaDto> filtros;
    private FiltroGrupoDto numerador;
    private FiltroGrupoDto denominador;
    private String campoValor;
    private String campoAgrupacion;
}
