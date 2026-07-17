package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ServicioResponseDto;
import com.preventiva.backend.entity.Servicio;
import com.preventiva.backend.repository.ServicioRepository;
import com.preventiva.backend.service.interfaces.ServicioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicioServiceImpl implements ServicioService {

    private final ServicioRepository servicioRepository;

    @Override
    public List<ServicioResponseDto> listarServicios() {
        return servicioRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    private ServicioResponseDto mapToDto(Servicio servicio) {
        return ServicioResponseDto.builder()
                .id(servicio.getId())
                .codigo(servicio.getCodigo())
                .nombre(servicio.getNombre())
                .descripcion(servicio.getDescripcion())
                .activo(servicio.getActivo())
                .build();
    }
}