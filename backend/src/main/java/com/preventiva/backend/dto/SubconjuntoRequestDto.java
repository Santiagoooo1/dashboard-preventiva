package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Petición de detalle del subconjunto activo (Fase 6.9H.3).
 *
 * `filtros` es EXACTAMENTE la misma lista que el dashboard cruzado envía a
 * /dashboard: filtros globales + selección gráfica ya traducida (categórica o
 * temporal). No se reconstruye nada desde etiquetas visibles.
 */
@Getter
@Setter
public class SubconjuntoRequestDto {

    private List<FiltroMetricaDto> filtros;

    /**
     * Campo que identifica al individuo, detectado por el frontend. El backend
     * lo VALIDA contra los CampoClinico del dataset antes de usarlo; si no
     * existe, el subconjunto se sirve sin vista de pacientes.
     */
    private String campoIndividuo;

    private Integer pagina;
    private Integer tamano;

    private String ordenCampo;
    /** ASC o DESC; cualquier otra cosa se trata como ASC. */
    private String ordenDireccion;

    /** Búsqueda por identificador, dentro del subconjunto ya filtrado. */
    private String busquedaIndividuo;
}
