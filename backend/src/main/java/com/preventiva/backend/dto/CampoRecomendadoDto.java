package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Una columna que el sistema recomienda como filtro o como dimensión de
 * agrupación (Fase 6.9J.2).
 *
 * <p>Es una sugerencia ordenada, no una restricción: el usuario puede seguir
 * filtrando y agrupando por cualquier campo compatible desde las herramientas
 * de siempre. Esto solo dice por dónde empezar.
 */
@Getter
@Setter
@Builder
public class CampoRecomendadoDto {

    private String codigo;
    private String etiqueta;

    /** Rol analítico del campo, por si la interfaz quiere agrupar la lista. */
    private String rol;

    /** Prioridad declarada por el usuario. */
    private String prioridadDashboard;

    /** Por qué se recomienda, en lenguaje clínico. */
    private String motivo;

    /** Orden de recomendación, empezando en 1. */
    private int orden;
}
