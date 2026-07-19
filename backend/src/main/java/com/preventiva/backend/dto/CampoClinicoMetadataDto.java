package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CampoClinicoMetadataDto {

    private Long id;
    private String codigo;
    private String etiqueta;
    private String tipoDato;
    private Boolean esComun;
    private Boolean obligatorio;
    private Boolean activo;
    private CampoRolesDto roles;
}
