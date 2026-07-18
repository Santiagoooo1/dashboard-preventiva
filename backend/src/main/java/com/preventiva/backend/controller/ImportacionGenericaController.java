package com.preventiva.backend.controller;

import com.preventiva.backend.dto.ErrorImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ValidacionFilasImportacionGenericaResponseDto;
import com.preventiva.backend.dto.ValidacionImportacionGenericaResponseDto;
import com.preventiva.backend.service.interfaces.ImportacionGenericaService;
import com.preventiva.backend.service.interfaces.ImportacionGenericaValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/importaciones-genericas")
@RequiredArgsConstructor
public class ImportacionGenericaController {

    private final ImportacionGenericaValidationService validationService;
    private final ImportacionGenericaService importacionGenericaService;

    @PostMapping("/validar")
    public ValidacionImportacionGenericaResponseDto validar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", required = false) Integer filaCabecera) {
        return validationService.validarCabeceras(archivo, plantillaId, indiceHoja, filaCabecera);
    }

    @PostMapping("/validar-filas")
    public ValidacionFilasImportacionGenericaResponseDto validarFilas(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", required = false) Integer filaCabecera) {
        return validationService.validarFilas(archivo, plantillaId, indiceHoja, filaCabecera);
    }

    @PostMapping("/importar")
    public ImportacionGenericaResponseDto importar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", required = false) Integer filaCabecera) {
        return importacionGenericaService.importar(archivo, plantillaId, indiceHoja, filaCabecera);
    }

    @GetMapping("/{id}")
    public ImportacionGenericaResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return importacionGenericaService.obtenerPorId(id);
    }

    @GetMapping("/{id}/errores")
    public List<ErrorImportacionGenericaResponseDto> listarErrores(@PathVariable("id") Long id) {
        return importacionGenericaService.listarErrores(id);
    }
}
