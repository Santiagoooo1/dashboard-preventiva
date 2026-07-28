package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.Hospital;
import com.preventiva.backend.entity.ImportacionGenerica;
import com.preventiva.backend.entity.ImportacionTrabajo;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.enums.EstadoDatasetClinico;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.ErrorImportacionGenericaRepository;
import com.preventiva.backend.repository.HospitalRepository;
import com.preventiva.backend.repository.ImportacionGenericaRepository;
import com.preventiva.backend.repository.ImportacionTrabajoRepository;
import com.preventiva.backend.repository.FilaImportacionTrabajoRepository;
import com.preventiva.backend.repository.MapeoCampoImportacionRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DatasetClinicoServiceImpl implements DatasetClinicoService {

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final HospitalRepository hospitalRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final PlantillaImportacionRepository plantillaImportacionRepository;
    private final MapeoCampoImportacionRepository mapeoCampoImportacionRepository;
    private final ImportacionTrabajoRepository importacionTrabajoRepository;
    private final FilaImportacionTrabajoRepository filaImportacionTrabajoRepository;
    private final ImportacionGenericaRepository importacionGenericaRepository;
    private final ErrorImportacionGenericaRepository errorImportacionGenericaRepository;
    private final RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    @Override
    public List<DatasetClinicoResponseDto> listar(boolean incluirBorradores) {
        return datasetClinicoRepository.findByActivoTrue()
                .stream()
                .filter(d -> incluirBorradores || esUtilizable(d))
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
                .estadoDataset(request.getEstadoDataset() != null ? request.getEstadoDataset() : EstadoDatasetClinico.ACTIVO)
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

    @Override
    public DatasetClinicoResponseDto activar(Long id) {
        DatasetClinico dataset = obtenerDatasetOLanzar(id);
        dataset.setEstadoDataset(EstadoDatasetClinico.ACTIVO);
        return mapToDto(datasetClinicoRepository.save(dataset));
    }

    @Override
    @Transactional
    public void descartarBorrador(Long id) {
        DatasetClinico dataset = obtenerDatasetOLanzar(id);
        EstadoDatasetClinico estado = dataset.getEstadoDataset();

        if (estado != EstadoDatasetClinico.BORRADOR && estado != EstadoDatasetClinico.VALIDANDO) {
            throw new IllegalArgumentException(
                    "Solo se pueden descartar datasets en borrador o en validación.");
        }

        if (registroClinicoGenericoRepository.existsByDatasetId(id)) {
            throw new IllegalArgumentException(
                    "Este dataset ya tiene registros importados; no puede descartarse como borrador.");
        }

        List<ImportacionTrabajo> trabajos = importacionTrabajoRepository.findByDatasetId(id);
        if (!trabajos.isEmpty()) {
            List<Long> idsTrabajos = trabajos.stream().map(ImportacionTrabajo::getId).toList();
            filaImportacionTrabajoRepository.deleteByImportacionTrabajoIdIn(idsTrabajos);
            importacionTrabajoRepository.deleteAll(trabajos);
        }

        List<PlantillaImportacion> plantillas = plantillaImportacionRepository.findByDatasetId(id);
        for (PlantillaImportacion plantilla : plantillas) {
            List<ImportacionGenerica> importaciones =
                    importacionGenericaRepository.findByPlantillaId(plantilla.getId());
            if (!importaciones.isEmpty()) {
                List<Long> idsImportaciones = importaciones.stream().map(ImportacionGenerica::getId).toList();
                errorImportacionGenericaRepository.deleteByImportacionGenericaIdIn(idsImportaciones);
                importacionGenericaRepository.deleteAll(importaciones);
            }
            mapeoCampoImportacionRepository.deleteByPlantillaId(plantilla.getId());
        }
        if (!plantillas.isEmpty()) {
            plantillaImportacionRepository.deleteAll(plantillas);
        }

        campoClinicoRepository.deleteByDatasetId(id);
        datasetClinicoRepository.delete(dataset);
    }

    private boolean esUtilizable(DatasetClinico dataset) {
        EstadoDatasetClinico estado = dataset.getEstadoDataset();
        return estado == null || estado == EstadoDatasetClinico.ACTIVO;
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
                .estadoDataset(dataset.getEstadoDataset() != null
                        ? dataset.getEstadoDataset().name()
                        : EstadoDatasetClinico.ACTIVO.name())
                .build();
    }
}
