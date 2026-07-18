package com.preventiva.backend.dto;

import com.preventiva.backend.enums.OperadorFiltro;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FiltroMetricaDto {

    private String campo;
    private OperadorFiltro operador;
    private Object valor;
}
