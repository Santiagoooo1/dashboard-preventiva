package com.preventiva.backend.dto;

import com.preventiva.backend.enums.Granularidad;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SerieTemporalRequestDto {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    @NotNull
    private Granularidad granularidad;

    private String campoFecha;
    private String campoSegmentacion;
}
