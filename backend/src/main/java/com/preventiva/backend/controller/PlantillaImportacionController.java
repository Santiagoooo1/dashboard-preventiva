package com.preventiva.backend.controller;

import com.preventiva.backend.dto.DeteccionColumnasResponseDto;
import com.preventiva.backend.dto.MapeoCampoImportacionRequestDto;
import com.preventiva.backend.dto.MapeoCampoImportacionResponseDto;
import com.preventiva.backend.dto.PlantillaImportacionRequestDto;
import com.preventiva.backend.dto.PlantillaImportacionResponseDto;
import com.preventiva.backend.service.interfaces.MapeoCampoImportacionService;
import com.preventiva.backend.service.interfaces.PlantillaImportacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlantillaImportacionController {

    private final PlantillaImportacionService plantillaImportacionService;
    private final MapeoCampoImportacionService mapeoCampoImportacionService;

    @GetMapping("/api/datasets-clinicos/{datasetId}/plantillas-importacion")
    public List<PlantillaImportacionResponseDto> listarPorDataset(@PathVariable("datasetId") Long datasetId) {
        return plantillaImportacionService.listarPorDataset(datasetId);
    }

    @PostMapping("/api/datasets-clinicos/{datasetId}/plantillas-importacion")
    @ResponseStatus(HttpStatus.CREATED)
    public PlantillaImportacionResponseDto crear(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody PlantillaImportacionRequestDto request) {
        return plantillaImportacionService.crear(datasetId, request);
    }

    @GetMapping("/api/plantillas-importacion/{id}")
    public PlantillaImportacionResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return plantillaImportacionService.obtenerPorId(id);
    }

    @PutMapping("/api/plantillas-importacion/{id}")
    public PlantillaImportacionResponseDto actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody PlantillaImportacionRequestDto request) {
        return plantillaImportacionService.actualizar(id, request);
    }

    @DeleteMapping("/api/plantillas-importacion/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable("id") Long id) {
        plantillaImportacionService.desactivar(id);
    }

    @PostMapping("/api/plantillas-importacion/detectar-columnas")
    public DeteccionColumnasResponseDto detectarColumnas(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", defaultValue = "0") Integer filaCabecera) {
        return plantillaImportacionService.detectarColumnas(archivo, indiceHoja, filaCabecera);
    }

    @GetMapping("/api/plantillas-importacion/{id}/mapeos")
    public List<MapeoCampoImportacionResponseDto> listarMapeos(@PathVariable("id") Long id) {
        return mapeoCampoImportacionService.listarPorPlantilla(id);
    }

    @PostMapping("/api/plantillas-importacion/{id}/mapeos")
    @ResponseStatus(HttpStatus.CREATED)
    public MapeoCampoImportacionResponseDto crearMapeo(
            @PathVariable("id") Long id,
            @Valid @RequestBody MapeoCampoImportacionRequestDto request) {
        return mapeoCampoImportacionService.crear(id, request);
    }

    @PutMapping("/api/plantillas-importacion/{id}/mapeos/{mapeoId}")
    public MapeoCampoImportacionResponseDto actualizarMapeo(
            @PathVariable("id") Long id,
            @PathVariable("mapeoId") Long mapeoId,
            @Valid @RequestBody MapeoCampoImportacionRequestDto request) {
        return mapeoCampoImportacionService.actualizar(id, mapeoId, request);
    }

    @DeleteMapping("/api/plantillas-importacion/{id}/mapeos/{mapeoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarMapeo(
            @PathVariable("id") Long id,
            @PathVariable("mapeoId") Long mapeoId) {
        mapeoCampoImportacionService.desactivar(id, mapeoId);
    }
}
