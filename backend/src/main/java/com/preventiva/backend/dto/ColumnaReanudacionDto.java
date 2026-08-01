package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ColumnaReanudacionDto {

    private String nombreOriginal;
    private String nombreVisible;
    private boolean usar;
    private String tipoDato;
    private String campoClinicoCodigo;
    private String campoClinicoEtiqueta;
    private boolean obligatorio;
    private boolean mapeada;
}
