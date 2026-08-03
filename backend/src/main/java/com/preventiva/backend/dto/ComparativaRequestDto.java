package com.preventiva.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class ComparativaRequestDto {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    @NotBlank
    private String campoAgrupacion;

    private List<FiltroMetricaDto> filtrosGlobales;
}
