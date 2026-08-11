package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Resumen ligero de una columna importada (Fase 6.9I.2).
 *
 * <p>Es lo que ve el usuario al elegir un campo en el constructor: cuántos
 * datos tiene, cuántos faltan, cuántos valores distintos y qué se puede
 * preguntar sobre ella. Deliberadamente NO contiene todos los valores de la
 * columna: solo un puñado de ejemplos acotado por LIMIT.
 */
@Getter
@Setter
@Builder
public class PerfilCampoDto {

    private String codigo;
    private String etiqueta;
    private String tipoDato;
    private Boolean activo;

    /** Relevancia para dashboards elegida por el usuario (no deducida). */
    private String prioridadDashboard;

    /** Rol analítico sugerido. El usuario puede corregirlo dentro de lo seguro. */
    private String rolSugerido;

    /** Roles a los que puede cambiarlo sin que el resultado deje de tener sentido. */
    private List<String> rolesAlternativos;

    private long totalRegistros;
    private long valoresInformados;
    private long valoresSinDato;
    private long valoresDistintos;

    /** BAJA, MEDIA o ALTA. Decide si el donut es legible y si hace falta Top N. */
    private String cardinalidad;

    /** Porcentaje de registros con el campo informado, redondeado a dos decimales. */
    private Double completitud;

    /** Solo para numérico y fecha; texto, porque una fecha no es un double. */
    private String valorMinimo;
    private String valorMaximo;

    /** Unos pocos valores reales, los más frecuentes primero. Siempre acotado. */
    private List<String> valoresEjemplo;

    /**
     * Operaciones ofrecidas para el rol sugerido, en orden de utilidad clínica.
     * Nunca vacío: toda columna activa admite al menos contar, contar distintos
     * y medir completitud.
     */
    private List<OperacionDisponibleDto> operaciones;

    /**
     * Lo mismo para cada rol que el usuario puede elegir (el sugerido incluido).
     *
     * <p>Va resuelto de antemano para que cambiar la interpretación en el
     * formulario no obligue a una nueva llamada ni a replicar en el frontend la
     * matriz de compatibilidad: quien decide qué es compatible sigue siendo el
     * backend.
     */
    private Map<String, List<OperacionDisponibleDto>> operacionesPorRol;

    /** Es el identificador individual del dataset (paciente). */
    private Boolean esIdentificadorIndividuo;
}
