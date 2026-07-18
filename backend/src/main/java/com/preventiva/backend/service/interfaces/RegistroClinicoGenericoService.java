package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoResponseDto;

import java.util.List;

public interface RegistroClinicoGenericoService {

    RegistroClinicoGenericoResponseDto crear(RegistroClinicoGenericoRequestDto request);

    List<RegistroClinicoGenericoResponseDto> listarPorDataset(Long datasetId);

    RegistroClinicoGenericoResponseDto obtenerPorId(Long id);
}
