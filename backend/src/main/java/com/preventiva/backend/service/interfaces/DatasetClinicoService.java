package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;

import java.util.List;

public interface DatasetClinicoService {

    List<DatasetClinicoResponseDto> listarActivos();

    DatasetClinicoResponseDto obtenerPorId(Long id);

    DatasetClinicoResponseDto crear(DatasetClinicoRequestDto request);

    DatasetClinicoResponseDto actualizar(Long id, DatasetClinicoRequestDto request);

    void desactivar(Long id);
}
