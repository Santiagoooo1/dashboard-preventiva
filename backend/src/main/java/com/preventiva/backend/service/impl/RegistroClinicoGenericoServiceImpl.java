package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoResponseDto;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.Hospital;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.HospitalRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RegistroClinicoGenericoServiceImpl implements RegistroClinicoGenericoService {

    private final RegistroClinicoGenericoRepository registroClinicoGenericoRepository;
    private final DatasetClinicoRepository datasetClinicoRepository;
    private final HospitalRepository hospitalRepository;

    @Override
    public RegistroClinicoGenericoResponseDto crear(RegistroClinicoGenericoRequestDto request) {
        DatasetClinico dataset = datasetClinicoRepository.findById(request.getDatasetId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el dataset con id: " + request.getDatasetId()));

        Hospital hospital = resolverHospital(request.getHospitalId());

        RegistroClinicoGenerico registro = RegistroClinicoGenerico.builder()
                .dataset(dataset)
                .pacienteCodigo(request.getPacienteCodigo())
                .fechaEvento(request.getFechaEvento())
                .servicio(request.getServicio())
                .tipoEvento(request.getTipoEvento())
                .procedimiento(request.getProcedimiento())
                .diagnostico(request.getDiagnostico())
                .edad(request.getEdad())
                .sexo(request.getSexo())
                .hospital(hospital)
                .datosDinamicos(request.getDatosDinamicos())
                .fechaCreacion(LocalDateTime.now())
                .build();

        return mapToDto(registroClinicoGenericoRepository.save(registro));
    }

    @Override
    public List<RegistroClinicoGenericoResponseDto> listarPorDataset(Long datasetId) {
        datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el dataset con id: " + datasetId));

        return registroClinicoGenericoRepository.findByDatasetId(datasetId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public RegistroClinicoGenericoResponseDto obtenerPorId(Long id) {
        return mapToDto(registroClinicoGenericoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el registro con id: " + id)));
    }

    private Hospital resolverHospital(Long hospitalId) {
        if (hospitalId == null) {
            return null;
        }

        return hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el hospital con id: " + hospitalId));
    }

    private RegistroClinicoGenericoResponseDto mapToDto(RegistroClinicoGenerico registro) {
        return RegistroClinicoGenericoResponseDto.builder()
                .id(registro.getId())
                .datasetId(registro.getDataset().getId())
                .pacienteCodigo(registro.getPacienteCodigo())
                .fechaEvento(registro.getFechaEvento())
                .servicio(registro.getServicio())
                .tipoEvento(registro.getTipoEvento())
                .procedimiento(registro.getProcedimiento())
                .diagnostico(registro.getDiagnostico())
                .edad(registro.getEdad())
                .sexo(registro.getSexo())
                .hospitalId(registro.getHospital() != null ? registro.getHospital().getId() : null)
                .hospitalNombre(registro.getHospital() != null ? registro.getHospital().getNombre() : null)
                .importacionId(registro.getImportacion() != null ? registro.getImportacion().getId() : null)
                .datosDinamicos(registro.getDatosDinamicos())
                .fechaCreacion(registro.getFechaCreacion())
                .build();
    }
}
