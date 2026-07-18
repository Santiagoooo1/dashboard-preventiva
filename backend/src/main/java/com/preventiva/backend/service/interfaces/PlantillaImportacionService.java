package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DeteccionColumnasResponseDto;
import com.preventiva.backend.dto.PlantillaImportacionRequestDto;
import com.preventiva.backend.dto.PlantillaImportacionResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PlantillaImportacionService {

    List<PlantillaImportacionResponseDto> listarPorDataset(Long datasetId);

    PlantillaImportacionResponseDto crear(Long datasetId, PlantillaImportacionRequestDto request);

    PlantillaImportacionResponseDto obtenerPorId(Long id);

    PlantillaImportacionResponseDto actualizar(Long id, PlantillaImportacionRequestDto request);

    void desactivar(Long id);

    DeteccionColumnasResponseDto detectarColumnas(
            MultipartFile archivo,
            Integer indiceHoja,
            Integer filaCabecera);
}
