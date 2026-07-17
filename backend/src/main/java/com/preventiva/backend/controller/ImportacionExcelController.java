package com.preventiva.backend.controller;

import com.preventiva.backend.dto.ErrorImportacionExcelResponseDto;
import com.preventiva.backend.dto.ImportacionExcelResponseDto;
import com.preventiva.backend.dto.ValidacionExcelResponseDto;
import com.preventiva.backend.dto.ValidacionFilasExcelResponseDto;
import com.preventiva.backend.service.interfaces.ExcelImportService;
import com.preventiva.backend.service.interfaces.ExcelValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/importaciones")
@RequiredArgsConstructor
public class ImportacionExcelController {

    private final ExcelValidationService excelValidationService;
    private final ExcelImportService excelImportService;

    @PostMapping("/validar")
    public ValidacionExcelResponseDto validarExcel(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja) {
        return excelValidationService.validarExcel(archivo, plantillaId, indiceHoja);
    }

    @PostMapping("/validar-filas")
    public ValidacionFilasExcelResponseDto validarFilasExcel(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", defaultValue = "0") Integer filaCabecera) {
        return excelValidationService.validarFilasExcel(
                archivo,
                plantillaId,
                indiceHoja,
                filaCabecera);
    }

    @PostMapping("/importar")
    public ImportacionExcelResponseDto importarExcel(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", defaultValue = "0") Integer filaCabecera) {
        return excelImportService.importarExcel(
                archivo,
                plantillaId,
                indiceHoja,
                filaCabecera);
    }

    @GetMapping("/{id}/errores")
    public List<ErrorImportacionExcelResponseDto> listarErrores(@PathVariable("id") Long id) {
        return excelImportService.listarErrores(id);
    }
}
