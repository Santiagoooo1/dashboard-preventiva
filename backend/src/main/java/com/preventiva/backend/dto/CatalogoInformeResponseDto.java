package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Todo lo que se puede insertar en un informe, en una sola llamada
 * (Fase 6.9Q.1).
 *
 * <p>Es puramente estructural: no ejecuta ninguna métrica. Abrir la biblioteca
 * del editor no tiene por qué recalcular medio hospital.
 */
@Getter
@Setter
@Builder
public class CatalogoInformeResponseDto {

    /** Dashboards con sus widgets ya configurados. Fuente principal. */
    private List<DashboardDisponibleDto> dashboards;

    /** Métricas que no están en ningún dashboard. Fuente secundaria. */
    private List<IndicadorDisponibleDto> indicadoresSinDashboard;
}
