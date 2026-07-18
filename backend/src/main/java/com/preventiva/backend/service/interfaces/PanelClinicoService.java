package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.EjecucionMetricaRequestDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.PanelClinicoResponseDto;
import com.preventiva.backend.dto.PanelEjecucionResponseDto;

import java.util.List;

public interface PanelClinicoService {

    List<PanelClinicoResponseDto> listarPorDataset(Long datasetId);

    PanelClinicoResponseDto obtenerPorId(Long id);

    PanelClinicoResponseDto crear(Long datasetId, PanelClinicoRequestDto request);

    PanelClinicoResponseDto actualizar(Long id, PanelClinicoRequestDto request);

    void desactivar(Long id);

    PanelEjecucionResponseDto ejecutar(Long id, EjecucionMetricaRequestDto request);
}
