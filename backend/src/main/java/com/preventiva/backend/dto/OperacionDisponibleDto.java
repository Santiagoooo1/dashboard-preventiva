package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Una operación ofrecida para un campo concreto, ya resuelta por el backend
 * (Fase 6.9I.2). El frontend la pinta; no decide cuáles existen.
 */
@Getter
@Setter
@Builder
public class OperacionDisponibleDto {

    /** Valor de {@code TipoMetrica}. */
    private String codigo;

    /** Cómo se llama en la pantalla: «Porcentaje de Sí», «Mediana»… */
    private String nombre;

    /** Cómo se calcula, en una frase, para la previsualización. */
    private String explicacion;

    /** Sufijo del código sugerido de la métrica: {@code edad} + {@code _promedio}. */
    private String sufijoCodigo;

    /** Forma del resultado: UNICO, AGRUPADO o SERIE. Determina las visualizaciones. */
    private String planResultado;

    /** Visualización recomendada por defecto. */
    private String visualizacionRecomendada;

    /** Se expresa en %, así que la unidad por defecto es «%». */
    private Boolean porcentual;

    /** Necesita recorte Top N por alta cardinalidad (ver {@code exigeTopN}). */
    private Boolean exigeTopN;

    /** Aviso a mostrar antes de elegirla; null si no hay ninguno. */
    private String advertencia;
}
