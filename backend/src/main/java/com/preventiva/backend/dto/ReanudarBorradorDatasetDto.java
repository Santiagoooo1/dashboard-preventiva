package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ReanudarBorradorDatasetDto {

    private Long datasetId;
    private String codigo;
    private String nombre;
    private String estadoDataset;
    private boolean puedeReanudarse;
    private String motivoNoReanudable;
    private String pasoRecomendado;
    private Long importacionTrabajoId;
    private Long plantillaId;
    private Long importacionGenericaId;
    private Integer totalFilasLeidas;
    private Integer totalErrores;
    private Integer totalAdvertencias;
    private Integer totalFilasExcluidas;
    private Boolean importable;
    private String estadoImportacionTrabajo;
    private String mensaje;
    /**
     * true cuando no hay ninguna copia de trabajo útil pero sí existió alguna
     * (quedó DESCARTADA): permite al frontend distinguir "nunca se subió
     * nada" de "se subió algo y se descartó" (Fase 6.8E.2.2).
     */
    private boolean huboCopiaDescartada;
}
