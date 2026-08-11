package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CampoClinicoServiceImpl implements CampoClinicoService {

    private final CampoClinicoRepository campoClinicoRepository;
    private final DatasetClinicoRepository datasetClinicoRepository;

    @Override
    public List<CampoClinicoResponseDto> listarPorDataset(Long datasetId) {
        obtenerDatasetOLanzar(datasetId);

        return campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public CampoClinicoResponseDto crear(Long datasetId, CampoClinicoRequestDto request) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);

        boolean yaExiste = campoClinicoRepository
                .existsByDatasetIdAndCodigoIgnoreCase(datasetId, request.getCodigo());

        if (yaExiste) {
            throw new IllegalArgumentException(
                    "Ya existe un campo clínico con el código '" + request.getCodigo()
                            + "' en este dataset.");
        }

        CampoClinico campo = CampoClinico.builder()
                .dataset(dataset)
                .codigo(request.getCodigo())
                .etiqueta(request.getEtiqueta())
                .tipoDato(request.getTipoDato())
                .esComun(request.getEsComun())
                .obligatorio(request.getObligatorio())
                .orden(request.getOrden())
                // Sin indicación explícita, un campo nuevo nace NORMAL: la
                // relevancia para dashboards la decide quien conoce el uso
                // clínico, no se deduce de cómo se importó.
                .prioridadDashboard(request.getPrioridadDashboard() != null
                        ? request.getPrioridadDashboard()
                        : PrioridadDashboardCampo.NORMAL)
                .activo(true)
                .build();

        return mapToDto(campoClinicoRepository.save(campo));
    }

    @Override
    public CampoClinicoResponseDto actualizar(Long datasetId, Long campoId, CampoClinicoRequestDto request) {
        obtenerDatasetOLanzar(datasetId);
        CampoClinico campo = obtenerCampoOLanzar(datasetId, campoId);

        boolean nombreDuplicado = campoClinicoRepository
                .findByDatasetIdAndCodigoIgnoreCase(datasetId, request.getCodigo())
                .filter(c -> !c.getId().equals(campoId))
                .isPresent();

        if (nombreDuplicado) {
            throw new IllegalArgumentException(
                    "Ya existe otro campo clínico con el código '" + request.getCodigo()
                            + "' en este dataset.");
        }

        campo.setCodigo(request.getCodigo());
        campo.setEtiqueta(request.getEtiqueta());
        campo.setTipoDato(request.getTipoDato());
        campo.setEsComun(request.getEsComun());
        campo.setObligatorio(request.getObligatorio());
        campo.setOrden(request.getOrden());

        // Solo se toca si viene: editar la etiqueta de un campo no debe
        // restablecer una prioridad que alguien ya había decidido.
        if (request.getPrioridadDashboard() != null) {
            campo.setPrioridadDashboard(request.getPrioridadDashboard());
        }

        return mapToDto(campoClinicoRepository.save(campo));
    }

    @Override
    public void desactivar(Long datasetId, Long campoId) {
        obtenerDatasetOLanzar(datasetId);
        CampoClinico campo = obtenerCampoOLanzar(datasetId, campoId);
        campo.setActivo(false);
        campoClinicoRepository.save(campo);
    }

    private DatasetClinico obtenerDatasetOLanzar(Long datasetId) {
        return datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));
    }

    private CampoClinico obtenerCampoOLanzar(Long datasetId, Long campoId) {
        CampoClinico campo = campoClinicoRepository.findById(campoId)
                .orElseThrow(() -> new NoSuchElementException("No existe el campo clínico con id: " + campoId));

        if (!campo.getDataset().getId().equals(datasetId)) {
            throw new NoSuchElementException(
                    "El campo " + campoId + " no pertenece al dataset " + datasetId);
        }

        return campo;
    }

    private CampoClinicoResponseDto mapToDto(CampoClinico campo) {
        return CampoClinicoResponseDto.builder()
                .id(campo.getId())
                .datasetId(campo.getDataset().getId())
                .codigo(campo.getCodigo())
                .etiqueta(campo.getEtiqueta())
                .tipoDato(campo.getTipoDato().name())
                .esComun(campo.getEsComun())
                .obligatorio(campo.getObligatorio())
                .orden(campo.getOrden())
                .activo(campo.getActivo())
                .prioridadDashboard(campo.getPrioridadDashboard().name())
                .build();
    }
}
