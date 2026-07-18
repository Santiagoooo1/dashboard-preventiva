package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FiltroGrupoDto {

    private List<FiltroMetricaDto> filtros;
}
