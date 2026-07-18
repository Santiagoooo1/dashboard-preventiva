package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.MapeoColumnaExcelRequestDto;
import com.preventiva.backend.dto.MapeoColumnaExcelResponseDto;

import java.util.List;

public interface MapeoColumnaExcelService {

    List<MapeoColumnaExcelResponseDto> listarPorPlantilla(Long plantillaId);

    MapeoColumnaExcelResponseDto crear(Long plantillaId, MapeoColumnaExcelRequestDto request);

    MapeoColumnaExcelResponseDto actualizar(Long plantillaId, Long mapeoId, MapeoColumnaExcelRequestDto request);

    void desactivar(Long plantillaId, Long mapeoId);
}
