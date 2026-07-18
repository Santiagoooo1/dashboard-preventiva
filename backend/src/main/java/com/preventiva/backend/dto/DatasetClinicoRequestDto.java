package com.preventiva.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DatasetClinicoRequestDto {

    @NotBlank
    private String codigo;

    @NotBlank
    private String nombre;

    private String descripcion;

    private Long hospitalId;
}
