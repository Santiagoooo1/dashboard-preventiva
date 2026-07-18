package com.preventiva.backend.dto;

import com.preventiva.backend.enums.OrigenImportacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlantillaImportacionRequestDto {

    @NotBlank
    private String nombre;

    private String descripcion;

    @NotNull
    private OrigenImportacion origen;

    private Integer filaCabecera;
}
