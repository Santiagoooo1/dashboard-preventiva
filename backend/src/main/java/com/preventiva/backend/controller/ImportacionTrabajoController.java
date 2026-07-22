package com.preventiva.backend.controller;

import com.preventiva.backend.dto.CrearImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.PaginaFilasImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.RevalidarImportacionTrabajoResponseDto;
import com.preventiva.backend.service.interfaces.ImportacionTrabajoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/importaciones-trabajo")
@RequiredArgsConstructor
public class ImportacionTrabajoController {

    private final ImportacionTrabajoService importacionTrabajoService;

    @PostMapping
    public CrearImportacionTrabajoResponseDto crear(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("plantillaId") Long plantillaId,
            @RequestParam(value = "indiceHoja", defaultValue = "0") Integer indiceHoja,
            @RequestParam(value = "filaCabecera", required = false) Integer filaCabecera) {
        return importacionTrabajoService.crear(archivo, plantillaId, indiceHoja, filaCabecera);
    }

    @GetMapping("/{id}")
    public ImportacionTrabajoResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return importacionTrabajoService.obtenerPorId(id);
    }

    @GetMapping("/{id}/filas")
    public PaginaFilasImportacionTrabajoResponseDto listarFilas(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "soloConErrores", defaultValue = "false") boolean soloConErrores) {
        return importacionTrabajoService.listarFilas(id, page, size, soloConErrores);
    }

    @GetMapping("/{id}/errores")
    public List<ErrorImportacionTrabajoDto> listarErrores(@PathVariable("id") Long id) {
        return importacionTrabajoService.listarErrores(id);
    }

    @PostMapping("/{id}/revalidar")
    public RevalidarImportacionTrabajoResponseDto revalidar(@PathVariable("id") Long id) {
        return importacionTrabajoService.revalidar(id);
    }

    @DeleteMapping("/{id}")
    public void descartar(@PathVariable("id") Long id) {
        importacionTrabajoService.descartar(id);
    }
}
