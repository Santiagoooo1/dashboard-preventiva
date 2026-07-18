package com.preventiva.backend.controller;

import com.preventiva.backend.dto.DeteccionColumnasResponseDto;
import com.preventiva.backend.dto.MapeoColumnaExcelRequestDto;
import com.preventiva.backend.dto.MapeoColumnaExcelResponseDto;
import com.preventiva.backend.dto.PlantillaExcelRequestDto;
import com.preventiva.backend.dto.PlantillaExcelResponseDto;
import com.preventiva.backend.service.interfaces.MapeoColumnaExcelService;
import com.preventiva.backend.service.interfaces.PlantillaExcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/plantillas-excel")
@RequiredArgsConstructor
public class PlantillaExcelController {

    private final PlantillaExcelService plantillaExcelService;
    private final MapeoColumnaExcelService mapeoColumnaExcelService;

    @GetMapping
    public List<PlantillaExcelResponseDto> listarPlantillasActivas() {
        return plantillaExcelService.listarPlantillasActivas();
    }

    @GetMapping("/{id}")
    public PlantillaExcelResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return plantillaExcelService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlantillaExcelResponseDto crear(@Valid @RequestBody PlantillaExcelRequestDto request) {
        return plantillaExcelService.crear(request);
    }

    @PutMapping("/{id}")
    public PlantillaExcelResponseDto actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody PlantillaExcelRequestDto request) {
        return plantillaExcelService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable("id") Long id) {
        plantillaExcelService.desactivar(id);
    }

    @PostMapping("/detectar-columnas")
    public DeteccionColumnasResponseDto detectarColumnas(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", defaultValue = "0") Integer filaCabecera) {
        return plantillaExcelService.detectarColumnas(archivo, indiceHoja, filaCabecera);
    }

    @GetMapping("/{id}/mapeos")
    public List<MapeoColumnaExcelResponseDto> listarMapeos(@PathVariable("id") Long id) {
        return mapeoColumnaExcelService.listarPorPlantilla(id);
    }

    @PostMapping("/{id}/mapeos")
    @ResponseStatus(HttpStatus.CREATED)
    public MapeoColumnaExcelResponseDto crearMapeo(
            @PathVariable("id") Long id,
            @Valid @RequestBody MapeoColumnaExcelRequestDto request) {
        return mapeoColumnaExcelService.crear(id, request);
    }

    @PutMapping("/{id}/mapeos/{mapeoId}")
    public MapeoColumnaExcelResponseDto actualizarMapeo(
            @PathVariable("id") Long id,
            @PathVariable("mapeoId") Long mapeoId,
            @Valid @RequestBody MapeoColumnaExcelRequestDto request) {
        return mapeoColumnaExcelService.actualizar(id, mapeoId, request);
    }

    @DeleteMapping("/{id}/mapeos/{mapeoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarMapeo(
            @PathVariable("id") Long id,
            @PathVariable("mapeoId") Long mapeoId) {
        mapeoColumnaExcelService.desactivar(id, mapeoId);
    }
}
