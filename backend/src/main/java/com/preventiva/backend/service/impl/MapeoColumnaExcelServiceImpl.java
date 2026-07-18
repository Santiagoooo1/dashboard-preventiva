package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.MapeoColumnaExcelRequestDto;
import com.preventiva.backend.dto.MapeoColumnaExcelResponseDto;
import com.preventiva.backend.entity.MapeoColumnaExcel;
import com.preventiva.backend.entity.PlantillaExcel;
import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.repository.MapeoColumnaExcelRepository;
import com.preventiva.backend.repository.PlantillaExcelRepository;
import com.preventiva.backend.service.interfaces.MapeoColumnaExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class MapeoColumnaExcelServiceImpl implements MapeoColumnaExcelService {

    private final MapeoColumnaExcelRepository mapeoColumnaExcelRepository;
    private final PlantillaExcelRepository plantillaExcelRepository;

    @Override
    public List<MapeoColumnaExcelResponseDto> listarPorPlantilla(Long plantillaId) {
        obtenerPlantillaOLanzar(plantillaId);

        return mapeoColumnaExcelRepository.findByPlantillaIdAndActivaTrue(plantillaId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public MapeoColumnaExcelResponseDto crear(Long plantillaId, MapeoColumnaExcelRequestDto request) {
        PlantillaExcel plantilla = obtenerPlantillaOLanzar(plantillaId);

        boolean yaExiste = mapeoColumnaExcelRepository
                .existsByPlantillaIdAndNombreColumnaExcelIgnoreCase(plantillaId, request.getNombreColumnaExcel());

        if (yaExiste) {
            throw new IllegalArgumentException(
                    "Ya existe un mapeo para la columna '" + request.getNombreColumnaExcel()
                            + "' en esta plantilla.");
        }

        MapeoColumnaExcel mapeo = MapeoColumnaExcel.builder()
                .plantilla(plantilla)
                .nombreColumnaExcel(request.getNombreColumnaExcel())
                .campoDestino(request.getCampoDestino())
                .tipoDato(request.getTipoDato())
                .obligatoria(request.getObligatoria())
                .politicaCampoFaltante(resolverPolitica(request))
                .activa(true)
                .build();

        return mapToDto(mapeoColumnaExcelRepository.save(mapeo));
    }

    @Override
    public MapeoColumnaExcelResponseDto actualizar(
            Long plantillaId, Long mapeoId, MapeoColumnaExcelRequestDto request) {
        obtenerPlantillaOLanzar(plantillaId);
        MapeoColumnaExcel mapeo = obtenerMapeoOLanzar(plantillaId, mapeoId);

        boolean nombreDuplicado = mapeoColumnaExcelRepository
                .findByPlantillaIdAndNombreColumnaExcelIgnoreCase(plantillaId, request.getNombreColumnaExcel())
                .filter(m -> !m.getId().equals(mapeoId))
                .isPresent();

        if (nombreDuplicado) {
            throw new IllegalArgumentException(
                    "Ya existe otro mapeo para la columna '" + request.getNombreColumnaExcel()
                            + "' en esta plantilla.");
        }

        mapeo.setNombreColumnaExcel(request.getNombreColumnaExcel());
        mapeo.setCampoDestino(request.getCampoDestino());
        mapeo.setTipoDato(request.getTipoDato());
        mapeo.setObligatoria(request.getObligatoria());
        mapeo.setPoliticaCampoFaltante(resolverPolitica(request));

        return mapToDto(mapeoColumnaExcelRepository.save(mapeo));
    }

    @Override
    public void desactivar(Long plantillaId, Long mapeoId) {
        obtenerPlantillaOLanzar(plantillaId);
        MapeoColumnaExcel mapeo = obtenerMapeoOLanzar(plantillaId, mapeoId);
        mapeo.setActiva(false);
        mapeoColumnaExcelRepository.save(mapeo);
    }

    private PoliticaCampoFaltante resolverPolitica(MapeoColumnaExcelRequestDto request) {
        if (request.getPoliticaCampoFaltante() != null) {
            return request.getPoliticaCampoFaltante();
        }

        return PoliticaCampoFaltante.porDefecto(Boolean.TRUE.equals(request.getObligatoria()));
    }

    private PlantillaExcel obtenerPlantillaOLanzar(Long plantillaId) {
        return plantillaExcelRepository.findById(plantillaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la plantilla con id: " + plantillaId));
    }

    private MapeoColumnaExcel obtenerMapeoOLanzar(Long plantillaId, Long mapeoId) {
        MapeoColumnaExcel mapeo = mapeoColumnaExcelRepository.findById(mapeoId)
                .orElseThrow(() -> new NoSuchElementException("No existe el mapeo con id: " + mapeoId));

        if (!mapeo.getPlantilla().getId().equals(plantillaId)) {
            throw new NoSuchElementException(
                    "El mapeo " + mapeoId + " no pertenece a la plantilla " + plantillaId);
        }

        return mapeo;
    }

    private MapeoColumnaExcelResponseDto mapToDto(MapeoColumnaExcel mapeo) {
        return MapeoColumnaExcelResponseDto.builder()
                .id(mapeo.getId())
                .plantillaId(mapeo.getPlantilla().getId())
                .nombreColumnaExcel(mapeo.getNombreColumnaExcel())
                .campoDestino(mapeo.getCampoDestino().name())
                .tipoDato(mapeo.getTipoDato().name())
                .obligatoria(mapeo.getObligatoria())
                .politicaCampoFaltante(
                        mapeo.getPoliticaCampoFaltante() != null
                                ? mapeo.getPoliticaCampoFaltante().name()
                                : null)
                .activa(mapeo.getActiva())
                .build();
    }
}
