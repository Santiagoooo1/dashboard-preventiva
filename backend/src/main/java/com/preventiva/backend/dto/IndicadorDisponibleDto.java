package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Una métrica que existe pero no está en ningún dashboard.
 *
 * <p>Se ofrece aparte a propósito: al insertarla hay que elegir cómo se
 * representa, mientras que un widget de dashboard ya trae esa decisión tomada.
 * Mezclarlas llevaría a inventar una visualización en silencio.
 */
@Getter
@Setter
@Builder
public class IndicadorDisponibleDto {

    private Long metricaId;
    private String nombre;
    private String tipoMetrica;
    private Long datasetId;
    private String datasetNombre;
}
