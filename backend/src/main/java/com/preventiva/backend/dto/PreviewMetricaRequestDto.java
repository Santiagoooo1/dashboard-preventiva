package com.preventiva.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PreviewMetricaRequestDto {

    @NotNull
    @Valid
    private MetricaClinicaRequestDto metrica;

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
}
