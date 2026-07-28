package com.preventiva.backend.dto;

import com.preventiva.backend.enums.EstadoDatasetClinico;
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

    /**
     * Opcional: si no se indica, el dataset se crea ACTIVO (comportamiento
     * histórico). El asistente guiado de /crear-dashboard envía BORRADOR para
     * no dejar datasets a medio terminar como si fueran utilizables.
     */
    private EstadoDatasetClinico estadoDataset;
}
