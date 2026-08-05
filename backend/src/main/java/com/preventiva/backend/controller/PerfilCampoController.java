package com.preventiva.backend.controller;

import com.preventiva.backend.dto.PerfilCamposResponseDto;
import com.preventiva.backend.service.interfaces.PerfilCampoService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PerfilCampoController {

    private final PerfilCampoService perfilCampoService;

    /**
     * Perfil analítico de las columnas de un dataset: lo que alimenta al
     * constructor de métricas desde columna.
     */
    @GetMapping("/api/datasets-clinicos/{datasetId}/campos-perfil")
    public PerfilCamposResponseDto obtenerPerfilCampos(@PathVariable("datasetId") Long datasetId) {
        return perfilCampoService.obtenerPerfilCampos(datasetId);
    }
}
