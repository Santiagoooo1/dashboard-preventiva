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
    /** Nombre del archivo que se subió en su día, para poder nombrarlo al reanudar. */
    private String nombreArchivoOriginal;
    /**
     * Celdas ya corregidas dentro de la aplicación que se conservan. Permite
     * decir "tus correcciones siguen ahí" en vez de dejar al usuario con la
     * duda de si tiene que rehacerlas.
     */
    private Integer totalCorrecciones;

    /**
     * Hay una revisión anterior guardada que el usuario PODRÍA querer
     * recuperar, pero que no se reactiva sola.
     *
     * <p>La copia está descartada y no consta que el descarte fuera
     * intencionado; eso no prueba que fuera un accidente. Se ofrece, y decide
     * el usuario con {@code POST /{id}/recuperar-trabajo-anterior}.
     */
    private boolean hayTrabajoHistoricoRecuperable;
    private Long trabajoHistoricoId;
    private String nombreArchivoHistorico;
    private Integer totalFilasHistoricas;
    private Integer totalCorreccionesHistoricas;
}
