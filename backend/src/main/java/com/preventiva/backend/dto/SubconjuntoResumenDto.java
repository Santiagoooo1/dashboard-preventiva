package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Resumen ligero: es lo único que se pide al crear una selección. Las tablas y
 * el perfil se cargan solo cuando el usuario abre el detalle.
 */
@Getter
@Setter
@Builder
public class SubconjuntoResumenDto {

    private long totalRegistros;
    /** COUNT DISTINCT real sobre TODO el subconjunto, no sobre una página. */
    private Long totalPacientesUnicos;
    private String campoIndividuo;
    private String etiquetaCampoIndividuo;
    /** false si el dataset no tiene identificador individual utilizable. */
    private boolean tienePacientes;
    private LocalDate periodoDesde;
    private LocalDate periodoHasta;
}
