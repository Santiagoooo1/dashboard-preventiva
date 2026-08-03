package com.preventiva.backend.dto;

import com.preventiva.backend.enums.Granularidad;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DashboardPanelRequestDto {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private Granularidad granularidad;
    private String campoFecha;
    private Map<String, String> variables;
    private List<FiltroMetricaDto> filtros;
}
