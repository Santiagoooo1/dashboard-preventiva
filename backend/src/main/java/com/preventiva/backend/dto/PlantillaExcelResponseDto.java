package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PlantillaExcelResponseDto {

    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Boolean activa;
    private Long servicioId;
    private String servicioCodigo;
    private String servicioNombre;
}