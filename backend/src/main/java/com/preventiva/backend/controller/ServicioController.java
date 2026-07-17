package com.preventiva.backend.controller;

import com.preventiva.backend.dto.ServicioResponseDto;
import com.preventiva.backend.service.interfaces.ServicioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicios")
@RequiredArgsConstructor
public class ServicioController {

    private final ServicioService servicioService;

    @GetMapping
    public List<ServicioResponseDto> listarServicios() {
        return servicioService.listarServicios();
    }
}