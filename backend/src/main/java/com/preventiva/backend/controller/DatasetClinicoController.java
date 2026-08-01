package com.preventiva.backend.controller;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.dto.ReanudarBorradorDatasetDto;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets-clinicos")
@RequiredArgsConstructor
public class DatasetClinicoController {

    private final DatasetClinicoService datasetClinicoService;
    private final CampoClinicoService campoClinicoService;

    @GetMapping
    public List<DatasetClinicoResponseDto> listar(
            @RequestParam(value = "incluirBorradores", defaultValue = "false") boolean incluirBorradores) {
        return datasetClinicoService.listar(incluirBorradores);
    }

    @GetMapping("/{id}")
    public DatasetClinicoResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return datasetClinicoService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetClinicoResponseDto crear(@Valid @RequestBody DatasetClinicoRequestDto request) {
        return datasetClinicoService.crear(request);
    }

    @PutMapping("/{id}")
    public DatasetClinicoResponseDto actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody DatasetClinicoRequestDto request) {
        return datasetClinicoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable("id") Long id) {
        datasetClinicoService.desactivar(id);
    }

    @PostMapping("/{id}/activar")
    public DatasetClinicoResponseDto activar(@PathVariable("id") Long id) {
        return datasetClinicoService.activar(id);
    }

    @DeleteMapping("/{id}/descartar-borrador")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartarBorrador(@PathVariable("id") Long id) {
        datasetClinicoService.descartarBorrador(id);
    }

    @GetMapping("/{id}/reanudar-borrador")
    public ReanudarBorradorDatasetDto reanudarBorrador(@PathVariable("id") Long id) {
        return datasetClinicoService.reanudarBorrador(id);
    }

    @GetMapping("/{id}/campos")
    public List<CampoClinicoResponseDto> listarCampos(@PathVariable("id") Long id) {
        return campoClinicoService.listarPorDataset(id);
    }

    @PostMapping("/{id}/campos")
    @ResponseStatus(HttpStatus.CREATED)
    public CampoClinicoResponseDto crearCampo(
            @PathVariable("id") Long id,
            @Valid @RequestBody CampoClinicoRequestDto request) {
        return campoClinicoService.crear(id, request);
    }

    @PutMapping("/{id}/campos/{campoId}")
    public CampoClinicoResponseDto actualizarCampo(
            @PathVariable("id") Long id,
            @PathVariable("campoId") Long campoId,
            @Valid @RequestBody CampoClinicoRequestDto request) {
        return campoClinicoService.actualizar(id, campoId, request);
    }

    @DeleteMapping("/{id}/campos/{campoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarCampo(
            @PathVariable("id") Long id,
            @PathVariable("campoId") Long campoId) {
        campoClinicoService.desactivar(id, campoId);
    }
}
