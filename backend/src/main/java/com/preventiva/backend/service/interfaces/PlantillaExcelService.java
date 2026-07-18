package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DeteccionColumnasResponseDto;
import com.preventiva.backend.dto.PlantillaExcelRequestDto;
import com.preventiva.backend.dto.PlantillaExcelResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PlantillaExcelService {

    List<PlantillaExcelResponseDto> listarPlantillasActivas();

    PlantillaExcelResponseDto obtenerPorId(Long id);

    PlantillaExcelResponseDto crear(PlantillaExcelRequestDto request);

    PlantillaExcelResponseDto actualizar(Long id, PlantillaExcelRequestDto request);

    void desactivar(Long id);

    DeteccionColumnasResponseDto detectarColumnas(
            MultipartFile archivo,
            Integer indiceHoja,
            Integer filaCabecera);
}