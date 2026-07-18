package com.preventiva.backend.controller;

import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoResponseDto;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registros-clinicos")
@RequiredArgsConstructor
public class RegistroClinicoGenericoController {

    private final RegistroClinicoGenericoService registroClinicoGenericoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroClinicoGenericoResponseDto crear(
            @Valid @RequestBody RegistroClinicoGenericoRequestDto request) {
        return registroClinicoGenericoService.crear(request);
    }

    @GetMapping
    public List<RegistroClinicoGenericoResponseDto> listarPorDataset(
            @RequestParam("datasetId") Long datasetId) {
        return registroClinicoGenericoService.listarPorDataset(datasetId);
    }

    @GetMapping("/{id}")
    public RegistroClinicoGenericoResponseDto obtenerPorId(@PathVariable("id") Long id) {
        return registroClinicoGenericoService.obtenerPorId(id);
    }
}
