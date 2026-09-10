package com.preventiva.backend.controller;

import com.preventiva.backend.dto.ComparacionInteranualRequestDto;
import com.preventiva.backend.dto.ComparacionInteranualResponseDto;
import com.preventiva.backend.service.interfaces.ComparacionInteranualService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comparaciones")
@RequiredArgsConstructor
public class ComparacionInteranualController {

    private final ComparacionInteranualService comparacionInteranualService;

    /**
     * Compara un concepto clínico entre varios años.
     *
     * <p>POST porque la petición lleva cuerpo (datasets, filtros, concepto), no
     * porque escriba: es una consulta y no persiste nada.
     */
    @PostMapping("/interanual")
    public ComparacionInteranualResponseDto interanual(
            @RequestBody ComparacionInteranualRequestDto request) {
        return comparacionInteranualService.comparar(request);
    }
}
