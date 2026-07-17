package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.PlantillaExcelResponseDto;

import java.util.List;

public interface PlantillaExcelService {

    List<PlantillaExcelResponseDto> listarPlantillasActivas();
}