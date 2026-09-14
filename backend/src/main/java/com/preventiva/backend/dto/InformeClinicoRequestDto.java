package com.preventiva.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InformeClinicoRequestDto {

    @NotBlank(message = "El informe necesita un nombre.")
    @Size(max = 150)
    private String nombre;

    @Size(max = 200)
    private String titulo;

    @Size(max = 1000)
    private String descripcion;
}
