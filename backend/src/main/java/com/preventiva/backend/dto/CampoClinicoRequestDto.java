package com.preventiva.backend.dto;

import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.TipoDatoExcel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampoClinicoRequestDto {

    @NotBlank
    private String codigo;

    @NotBlank
    private String etiqueta;

    @NotNull
    private TipoDatoExcel tipoDato;

    @NotNull
    private Boolean esComun;

    @NotNull
    private Boolean obligatorio;

    private Integer orden;

    /**
     * Relevancia para dashboards. Opcional: si no viene, se conserva la que
     * tuviera el campo (o NORMAL al crearlo). Así un cliente que solo edite la
     * etiqueta no borra sin querer una prioridad ya decidida.
     */
    private PrioridadDashboardCampo prioridadDashboard;
}
