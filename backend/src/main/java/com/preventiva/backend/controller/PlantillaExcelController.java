package com.preventiva.backend.controller;

import com.preventiva.backend.dto.PlantillaExcelResponseDto;
import com.preventiva.backend.service.interfaces.PlantillaExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plantillas-excel")
@RequiredArgsConstructor
public class PlantillaExcelController {

    private final PlantillaExcelService plantillaExcelService;

    @GetMapping
    public List<PlantillaExcelResponseDto> listarPlantillasActivas() {
        return plantillaExcelService.listarPlantillasActivas();
    }
}