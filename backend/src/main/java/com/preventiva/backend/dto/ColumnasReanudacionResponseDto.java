package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ColumnasReanudacionResponseDto {

    private Long importacionTrabajoId;
    private Long datasetId;
    private Long plantillaId;
    private List<ColumnaReanudacionDto> columnas;
    /** No nulo solo cuando no se pudieron reconstruir columnas (columnas vacía). */
    private String mensaje;
}
