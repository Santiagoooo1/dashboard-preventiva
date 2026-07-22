package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Página estable de filas de la copia de trabajo (evita serializar
 * directamente {@code PageImpl}, cuyo formato JSON no está garantizado).
 */
@Getter
@Setter
@Builder
public class PaginaFilasImportacionTrabajoResponseDto {

    private List<FilaImportacionTrabajoResponseDto> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
