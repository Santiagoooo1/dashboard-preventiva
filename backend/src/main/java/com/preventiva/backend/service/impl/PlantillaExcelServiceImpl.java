package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.PlantillaExcelResponseDto;
import com.preventiva.backend.entity.PlantillaExcel;
import com.preventiva.backend.repository.PlantillaExcelRepository;
import com.preventiva.backend.service.interfaces.PlantillaExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantillaExcelServiceImpl implements PlantillaExcelService {

    private final PlantillaExcelRepository plantillaExcelRepository;

    @Override
    public List<PlantillaExcelResponseDto> listarPlantillasActivas() {
        return plantillaExcelRepository.findByActivaTrue()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    private PlantillaExcelResponseDto mapToDto(PlantillaExcel plantilla) {
        return PlantillaExcelResponseDto.builder()
                .id(plantilla.getId())
                .codigo(plantilla.getCodigo())
                .nombre(plantilla.getNombre())
                .descripcion(plantilla.getDescripcion())
                .activa(plantilla.getActiva())
                .servicioId(
                        plantilla.getServicio() != null
                                ? plantilla.getServicio().getId()
                                : null)
                .servicioCodigo(
                        plantilla.getServicio() != null
                                ? plantilla.getServicio().getCodigo()
                                : null)
                .servicioNombre(
                        plantilla.getServicio() != null
                                ? plantilla.getServicio().getNombre()
                                : null)
                .build();
    }
}