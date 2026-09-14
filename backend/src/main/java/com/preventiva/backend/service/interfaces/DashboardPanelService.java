package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardPanelResponseDto;
import com.preventiva.backend.dto.DashboardWidgetDto;
import com.preventiva.backend.entity.PanelMetrica;

public interface DashboardPanelService {

    DashboardPanelResponseDto obtenerDashboard(Long panelId, DashboardPanelRequestDto request);

    /**
     * Calcula un widget suelto, sin que pertenezca a ningún panel guardado.
     *
     * <p>Existe para los informes (Fase 6.9Q): un bloque de informe apunta a una
     * métrica con su visualización igual que lo hace un widget, así que puede
     * describirse con un {@link PanelMetrica} transitorio y pasar por este mismo
     * cálculo. La alternativa —una segunda ruta analítica para los informes—
     * acabaría dando cifras distintas de las del dashboard.
     *
     * <p>No persiste nada: solo lee del {@code PanelMetrica} que se le pasa.
     */
    DashboardWidgetDto calcularWidget(PanelMetrica panelMetrica, DashboardPanelRequestDto filtros);
}
