package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PlantillaImportacionResponseDto {

    private Long id;
    private Long datasetId;
    private String datasetCodigo;
    private String nombre;
    private String descripcion;
    private String origen;
    private Integer filaCabecera;
    private Boolean activa;
}
