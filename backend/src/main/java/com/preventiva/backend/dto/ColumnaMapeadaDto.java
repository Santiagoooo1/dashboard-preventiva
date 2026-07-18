package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ColumnaMapeadaDto {

    private Integer indiceColumna;
    private String nombreColumna;
    private Boolean reconocida;
    private String campoClinicoCodigo;
    private String tipoDato;
}
