package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ColumnaDetectadaResponseDto;
import com.preventiva.backend.dto.DeteccionColumnasResponseDto;
import com.preventiva.backend.dto.PlantillaExcelRequestDto;
import com.preventiva.backend.dto.PlantillaExcelResponseDto;
import com.preventiva.backend.entity.PlantillaExcel;
import com.preventiva.backend.entity.Servicio;
import com.preventiva.backend.repository.PlantillaExcelRepository;
import com.preventiva.backend.repository.ServicioRepository;
import com.preventiva.backend.service.interfaces.PlantillaExcelService;
import com.preventiva.backend.util.TextNormalizer;
import com.preventiva.backend.util.WorkbookLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PlantillaExcelServiceImpl implements PlantillaExcelService {

    private final PlantillaExcelRepository plantillaExcelRepository;
    private final ServicioRepository servicioRepository;

    @Override
    public List<PlantillaExcelResponseDto> listarPlantillasActivas() {
        return plantillaExcelRepository.findByActivaTrue()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public PlantillaExcelResponseDto obtenerPorId(Long id) {
        return mapToDto(obtenerPlantillaOLanzar(id));
    }

    @Override
    public PlantillaExcelResponseDto crear(PlantillaExcelRequestDto request) {
        plantillaExcelRepository.findByCodigo(request.getCodigo())
                .ifPresent(p -> {
                    throw new IllegalArgumentException(
                            "Ya existe una plantilla con el código: " + request.getCodigo());
                });

        Servicio servicio = resolverServicio(request.getServicioId());

        PlantillaExcel plantilla = PlantillaExcel.builder()
                .codigo(request.getCodigo())
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .servicio(servicio)
                .activa(true)
                .build();

        return mapToDto(plantillaExcelRepository.save(plantilla));
    }

    @Override
    public PlantillaExcelResponseDto actualizar(Long id, PlantillaExcelRequestDto request) {
        PlantillaExcel plantilla = obtenerPlantillaOLanzar(id);

        plantillaExcelRepository.findByCodigo(request.getCodigo())
                .filter(p -> !p.getId().equals(id))
                .ifPresent(p -> {
                    throw new IllegalArgumentException(
                            "Ya existe otra plantilla con el código: " + request.getCodigo());
                });

        Servicio servicio = resolverServicio(request.getServicioId());

        plantilla.setCodigo(request.getCodigo());
        plantilla.setNombre(request.getNombre());
        plantilla.setDescripcion(request.getDescripcion());
        plantilla.setServicio(servicio);

        return mapToDto(plantillaExcelRepository.save(plantilla));
    }

    @Override
    public void desactivar(Long id) {
        PlantillaExcel plantilla = obtenerPlantillaOLanzar(id);
        plantilla.setActiva(false);
        plantillaExcelRepository.save(plantilla);
    }

    @Override
    public DeteccionColumnasResponseDto detectarColumnas(
            MultipartFile archivo,
            Integer indiceHoja,
            Integer filaCabecera) {
        LinkedHashMap<Integer, String> cabeceras = WorkbookLoader.leerCabecerasConIndice(
                archivo, indiceHoja, filaCabecera);

        List<ColumnaDetectadaResponseDto> columnas = cabeceras.entrySet()
                .stream()
                .map(entry -> ColumnaDetectadaResponseDto.builder()
                        .indiceColumna(entry.getKey())
                        .nombreOriginal(entry.getValue())
                        .nombreNormalizado(TextNormalizer.normalize(entry.getValue()))
                        .build())
                .toList();

        return DeteccionColumnasResponseDto.builder()
                .nombreArchivo(archivo.getOriginalFilename())
                .totalColumnas(columnas.size())
                .columnas(columnas)
                .build();
    }

    private PlantillaExcel obtenerPlantillaOLanzar(Long id) {
        return plantillaExcelRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe la plantilla con id: " + id));
    }

    private Servicio resolverServicio(Long servicioId) {
        if (servicioId == null) {
            return null;
        }

        return servicioRepository.findById(servicioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el servicio con id: " + servicioId));
    }

    private PlantillaExcelResponseDto mapToDto(PlantillaExcel plantilla) {
        return PlantillaExcelResponseDto.builder()
                .id(plantilla.getId())
                .codigo(plantilla.getCodigo())
                .nombre(plantilla.getNombre())
                .descripcion(plantilla.getDescripcion())
                .activa(plantilla.getActiva())
                .servicioId(
                        plantilla.getServicio() != null
                                ? plantilla.getServicio().getId()
                                : null)
                .servicioCodigo(
                        plantilla.getServicio() != null
                                ? plantilla.getServicio().getCodigo()
                                : null)
                .servicioNombre(
                        plantilla.getServicio() != null
                                ? plantilla.getServicio().getNombre()
                                : null)
                .build();
    }
}