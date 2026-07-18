package com.preventiva.backend.dto;

import com.preventiva.backend.enums.CampoDestino;
import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.enums.TipoDatoExcel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MapeoColumnaExcelRequestDto {

    @NotBlank
    private String nombreColumnaExcel;

    @NotNull
    private CampoDestino campoDestino;

    @NotNull
    private TipoDatoExcel tipoDato;

    @NotNull
    private Boolean obligatoria;

    private PoliticaCampoFaltante politicaCampoFaltante;
}
