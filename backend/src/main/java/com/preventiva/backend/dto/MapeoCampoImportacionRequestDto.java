package com.preventiva.backend.dto;

import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.enums.TipoDatoExcel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MapeoCampoImportacionRequestDto {

    @NotBlank
    private String nombreColumnaOrigen;

    @NotNull
    private Long campoClinicoId;

    @NotNull
    private TipoDatoExcel tipoDato;

    @NotNull
    private Boolean obligatorio;

    private PoliticaCampoFaltante politicaCampoFaltante;

    private String valorPorDefecto;

    private Integer orden;
}
