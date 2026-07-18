package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class DashboardFiltrosAplicadosDto {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private String granularidad;
}
