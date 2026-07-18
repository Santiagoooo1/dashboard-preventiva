package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;

import java.util.List;

public interface CampoClinicoService {

    List<CampoClinicoResponseDto> listarPorDataset(Long datasetId);

    CampoClinicoResponseDto crear(Long datasetId, CampoClinicoRequestDto request);

    CampoClinicoResponseDto actualizar(Long datasetId, Long campoId, CampoClinicoRequestDto request);

    void desactivar(Long datasetId, Long campoId);
}
