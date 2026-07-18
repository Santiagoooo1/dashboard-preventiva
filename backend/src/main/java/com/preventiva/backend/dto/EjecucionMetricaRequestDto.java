package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EjecucionMetricaRequestDto {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
}
