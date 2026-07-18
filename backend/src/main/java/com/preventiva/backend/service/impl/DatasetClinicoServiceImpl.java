package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.Hospital;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.HospitalRepository;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DatasetClinicoServiceImpl implements DatasetClinicoService {

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final HospitalRepository hospitalRepository;

    @Override
    public List<DatasetClinicoResponseDto> listarActivos() {
        return datasetClinicoRepository.findByActivoTrue()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public DatasetClinicoResponseDto obtenerPorId(Long id) {
        return mapToDto(obtenerDatasetOLanzar(id));
    }

    @Override
    public DatasetClinicoResponseDto crear(DatasetClinicoRequestDto request) {
        datasetClinicoRepository.findByCodigo(request.getCodigo())
                .ifPresent(d -> {
                    throw new IllegalArgumentException(
                            "Ya existe un dataset con el código: " + request.getCodigo());
                });

        Hospital hospital = resolverHospital(request.getHospitalId());

        DatasetClinico dataset = DatasetClinico.builder()
                .codigo(request.getCodigo())
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .hospital(hospital)
                .activo(true)
                .build();

        return mapToDto(datasetClinicoRepository.save(dataset));
    }

    @Override
    public DatasetClinicoResponseDto actualizar(Long id, DatasetClinicoRequestDto request) {
        DatasetClinico dataset = obtenerDatasetOLanzar(id);

        datasetClinicoRepository.findByCodigo(request.getCodigo())
                .filter(d -> !d.getId().equals(id))
                .ifPresent(d -> {
                    throw new IllegalArgumentException(
                            "Ya existe otro dataset con el código: " + request.getCodigo());
                });

        Hospital hospital = resolverHospital(request.getHospitalId());

        dataset.setCodigo(request.getCodigo());
        dataset.setNombre(request.getNombre());
        dataset.setDescripcion(request.getDescripcion());
        dataset.setHospital(hospital);

        return mapToDto(datasetClinicoRepository.save(dataset));
    }

    @Override
    public void desactivar(Long id) {
        DatasetClinico dataset = obtenerDatasetOLanzar(id);
        dataset.setActivo(false);
        datasetClinicoRepository.save(dataset);
    }

    private DatasetClinico obtenerDatasetOLanzar(Long id) {
        return datasetClinicoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + id));
    }

    private Hospital resolverHospital(Long hospitalId) {
        if (hospitalId == null) {
            return null;
        }

        return hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el hospital con id: " + hospitalId));
    }

    private DatasetClinicoResponseDto mapToDto(DatasetClinico dataset) {
        return DatasetClinicoResponseDto.builder()
                .id(dataset.getId())
                .codigo(dataset.getCodigo())
                .nombre(dataset.getNombre())
                .descripcion(dataset.getDescripcion())
                .hospitalId(dataset.getHospital() != null ? dataset.getHospital().getId() : null)
                .hospitalNombre(dataset.getHospital() != null ? dataset.getHospital().getNombre() : null)
                .activo(dataset.getActivo())
                .build();
    }
}
