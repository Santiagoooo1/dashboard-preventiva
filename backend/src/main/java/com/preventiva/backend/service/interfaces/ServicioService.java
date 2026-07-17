package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ServicioResponseDto;

import java.util.List;

public interface ServicioService {

    List<ServicioResponseDto> listarServicios();
}