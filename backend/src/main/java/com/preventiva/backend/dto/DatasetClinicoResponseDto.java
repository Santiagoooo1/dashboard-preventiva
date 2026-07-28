package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DatasetClinicoResponseDto {

    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Long hospitalId;
    private String hospitalNombre;
    private Boolean activo;
    private String estadoDataset;
}
