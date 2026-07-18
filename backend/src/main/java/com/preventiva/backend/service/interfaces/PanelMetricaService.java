package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.PanelMetricaConfiguracionWidgetRequestDto;
import com.preventiva.backend.dto.PanelMetricaRequestDto;
import com.preventiva.backend.dto.PanelMetricaResponseDto;

import java.util.List;

public interface PanelMetricaService {

    List<PanelMetricaResponseDto> listarPorPanel(Long panelId);

    PanelMetricaResponseDto crear(Long panelId, PanelMetricaRequestDto request);

    PanelMetricaResponseDto actualizar(Long panelId, Long panelMetricaId, PanelMetricaRequestDto request);

    void desactivar(Long panelId, Long panelMetricaId);

    PanelMetricaResponseDto actualizarConfiguracionWidget(
            Long panelId, Long panelMetricaId, PanelMetricaConfiguracionWidgetRequestDto request);
}
