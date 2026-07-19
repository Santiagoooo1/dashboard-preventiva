package com.preventiva.backend.controller;

import com.preventiva.backend.dto.CatalogoFrontendResponseDto;
import com.preventiva.backend.service.interfaces.FrontendCatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FrontendCatalogoController {

    private final FrontendCatalogoService frontendCatalogoService;

    @GetMapping("/api/frontend/catalogo")
    public CatalogoFrontendResponseDto obtenerCatalogo() {
        return frontendCatalogoService.obtenerCatalogo();
    }
}
