package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.MapeoCampoImportacionRequestDto;
import com.preventiva.backend.dto.MapeoCampoImportacionResponseDto;

import java.util.List;

public interface MapeoCampoImportacionService {

    List<MapeoCampoImportacionResponseDto> listarPorPlantilla(Long plantillaId);

    MapeoCampoImportacionResponseDto crear(Long plantillaId, MapeoCampoImportacionRequestDto request);

    MapeoCampoImportacionResponseDto actualizar(
            Long plantillaId, Long mapeoId, MapeoCampoImportacionRequestDto request);

    void desactivar(Long plantillaId, Long mapeoId);
}
