package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.ColumnaDetectadaResponseDto;
import com.preventiva.backend.dto.DeteccionColumnasResponseDto;
import com.preventiva.backend.dto.PlantillaImportacionRequestDto;
import com.preventiva.backend.dto.PlantillaImportacionResponseDto;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.service.interfaces.PlantillaImportacionService;
import com.preventiva.backend.util.CatalogoColumnasClinicas;
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
public class PlantillaImportacionServiceImpl implements PlantillaImportacionService {

    private final PlantillaImportacionRepository plantillaImportacionRepository;
    private final DatasetClinicoRepository datasetClinicoRepository;

    @Override
    public List<PlantillaImportacionResponseDto> listarPorDataset(Long datasetId) {
        obtenerDatasetOLanzar(datasetId);

        return plantillaImportacionRepository.findByDatasetIdAndActivaTrue(datasetId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public PlantillaImportacionResponseDto crear(Long datasetId, PlantillaImportacionRequestDto request) {
        DatasetClinico dataset = obtenerDatasetOLanzar(datasetId);

        if (!Boolean.TRUE.equals(dataset.getActivo())) {
            throw new IllegalArgumentException("El dataset está desactivado; no se pueden crear plantillas.");
        }

        PlantillaImportacion plantilla = PlantillaImportacion.builder()
                .dataset(dataset)
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .origen(request.getOrigen())
                .filaCabecera(request.getFilaCabecera() != null ? request.getFilaCabecera() : 0)
                .activa(true)
                .build();

        return mapToDto(plantillaImportacionRepository.save(plantilla));
    }

    @Override
    public PlantillaImportacionResponseDto obtenerPorId(Long id) {
        return mapToDto(obtenerPlantillaOLanzar(id));
    }

    @Override
    public PlantillaImportacionResponseDto actualizar(Long id, PlantillaImportacionRequestDto request) {
        PlantillaImportacion plantilla = obtenerPlantillaOLanzar(id);

        plantilla.setNombre(request.getNombre());
        plantilla.setDescripcion(request.getDescripcion());
        plantilla.setOrigen(request.getOrigen());
        plantilla.setFilaCabecera(request.getFilaCabecera() != null ? request.getFilaCabecera() : 0);

        return mapToDto(plantillaImportacionRepository.save(plantilla));
    }

    @Override
    public void desactivar(Long id) {
        PlantillaImportacion plantilla = obtenerPlantillaOLanzar(id);
        plantilla.setActiva(false);
        plantillaImportacionRepository.save(plantilla);
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
                .map(entry -> {
                    ColumnaDetectadaResponseDto.ColumnaDetectadaResponseDtoBuilder builder =
                            ColumnaDetectadaResponseDto.builder()
                                    .indiceColumna(entry.getKey())
                                    .nombreOriginal(entry.getValue())
                                    .nombreNormalizado(TextNormalizer.normalize(entry.getValue()));
                    // Columnas clínicas conocidas: se resuelven aquí para que el
                    // asistente no tenga que deducir por el nombre algo que ya
                    // sabemos con certeza.
                    CatalogoColumnasClinicas.resolver(entry.getValue()).ifPresent(canonica -> builder
                            .codigoCanonico(canonica.codigo())
                            .tipoDatoCanonico(canonica.tipoDato().name())
                            .esComunCanonico(canonica.esComun())
                            .etiquetaCanonica(canonica.etiquetaRecomendada())
                            .origenReconocimiento(canonica.origen().name()));
                    return builder.build();
                })
                .toList();

        return DeteccionColumnasResponseDto.builder()
                .nombreArchivo(archivo.getOriginalFilename())
                .totalColumnas(columnas.size())
                .columnas(columnas)
                .build();
    }

    private PlantillaImportacion obtenerPlantillaOLanzar(Long id) {
        return plantillaImportacionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la plantilla de importación con id: " + id));
    }

    private DatasetClinico obtenerDatasetOLanzar(Long datasetId) {
        return datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));
    }

    private PlantillaImportacionResponseDto mapToDto(PlantillaImportacion plantilla) {
        return PlantillaImportacionResponseDto.builder()
                .id(plantilla.getId())
                .datasetId(plantilla.getDataset().getId())
                .datasetCodigo(plantilla.getDataset().getCodigo())
                .nombre(plantilla.getNombre())
                .descripcion(plantilla.getDescripcion())
                .origen(plantilla.getOrigen() != null ? plantilla.getOrigen().name() : null)
                .filaCabecera(plantilla.getFilaCabecera())
                .activa(plantilla.getActiva())
                .build();
    }
}
