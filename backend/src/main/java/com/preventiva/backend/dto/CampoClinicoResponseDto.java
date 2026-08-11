package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CampoClinicoResponseDto {

    private Long id;
    private Long datasetId;
    private String codigo;
    private String etiqueta;
    private String tipoDato;
    private Boolean esComun;
    private Boolean obligatorio;
    private Integer orden;
    private Boolean activo;

    /** Relevancia para dashboards, independiente de esComun y obligatorio. */
    private String prioridadDashboard;
}
