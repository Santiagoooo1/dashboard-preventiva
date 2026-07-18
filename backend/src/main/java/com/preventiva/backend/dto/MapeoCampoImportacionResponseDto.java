package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MapeoCampoImportacionResponseDto {

    private Long id;
    private Long plantillaId;
    private String nombreColumnaOrigen;
    private Long campoClinicoId;
    private String campoClinicoCodigo;
    private String campoClinicoEtiqueta;
    private String tipoDato;
    private Boolean obligatorio;
    private String politicaCampoFaltante;
    private String valorPorDefecto;
    private Integer orden;
    private Boolean activo;
}
