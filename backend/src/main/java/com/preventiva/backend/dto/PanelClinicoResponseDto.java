package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PanelClinicoResponseDto {

    private Long id;
    private Long datasetId;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer orden;
    private Boolean activo;
}
