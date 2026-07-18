package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.MapeoCampoImportacionRequestDto;
import com.preventiva.backend.dto.MapeoCampoImportacionResponseDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.MapeoCampoImportacion;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.MapeoCampoImportacionRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.service.interfaces.MapeoCampoImportacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class MapeoCampoImportacionServiceImpl implements MapeoCampoImportacionService {

    private final MapeoCampoImportacionRepository mapeoCampoImportacionRepository;
    private final PlantillaImportacionRepository plantillaImportacionRepository;
    private final CampoClinicoRepository campoClinicoRepository;

    @Override
    public List<MapeoCampoImportacionResponseDto> listarPorPlantilla(Long plantillaId) {
        obtenerPlantillaOLanzar(plantillaId);

        return mapeoCampoImportacionRepository.findByPlantillaIdAndActivoTrue(plantillaId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public MapeoCampoImportacionResponseDto crear(Long plantillaId, MapeoCampoImportacionRequestDto request) {
        PlantillaImportacion plantilla = obtenerPlantillaActivaOLanzar(plantillaId);
        CampoClinico campoClinico = resolverCampoClinico(plantilla, request.getCampoClinicoId());

        validarNombreColumnaNoDuplicado(plantillaId, request.getNombreColumnaOrigen(), null);
        validarCampoClinicoNoDuplicado(plantillaId, campoClinico.getId(), null);

        MapeoCampoImportacion mapeo = MapeoCampoImportacion.builder()
                .plantilla(plantilla)
                .nombreColumnaOrigen(request.getNombreColumnaOrigen())
                .campoClinico(campoClinico)
                .tipoDato(request.getTipoDato())
                .obligatorio(request.getObligatorio())
                .politicaCampoFaltante(resolverPolitica(request))
                .valorPorDefecto(request.getValorPorDefecto())
                .orden(request.getOrden())
                .activo(true)
                .build();

        return mapToDto(mapeoCampoImportacionRepository.save(mapeo));
    }

    @Override
    public MapeoCampoImportacionResponseDto actualizar(
            Long plantillaId, Long mapeoId, MapeoCampoImportacionRequestDto request) {
        PlantillaImportacion plantilla = obtenerPlantillaActivaOLanzar(plantillaId);
        MapeoCampoImportacion mapeo = obtenerMapeoOLanzar(plantillaId, mapeoId);
        CampoClinico campoClinico = resolverCampoClinico(plantilla, request.getCampoClinicoId());

        validarNombreColumnaNoDuplicado(plantillaId, request.getNombreColumnaOrigen(), mapeoId);
        validarCampoClinicoNoDuplicado(plantillaId, campoClinico.getId(), mapeoId);

        mapeo.setNombreColumnaOrigen(request.getNombreColumnaOrigen());
        mapeo.setCampoClinico(campoClinico);
        mapeo.setTipoDato(request.getTipoDato());
        mapeo.setObligatorio(request.getObligatorio());
        mapeo.setPoliticaCampoFaltante(resolverPolitica(request));
        mapeo.setValorPorDefecto(request.getValorPorDefecto());
        mapeo.setOrden(request.getOrden());

        return mapToDto(mapeoCampoImportacionRepository.save(mapeo));
    }

    @Override
    public void desactivar(Long plantillaId, Long mapeoId) {
        obtenerPlantillaOLanzar(plantillaId);
        MapeoCampoImportacion mapeo = obtenerMapeoOLanzar(plantillaId, mapeoId);
        mapeo.setActivo(false);
        mapeoCampoImportacionRepository.save(mapeo);
    }

    private void validarNombreColumnaNoDuplicado(Long plantillaId, String nombreColumnaOrigen, Long mapeoIdExcluido) {
        boolean duplicado = mapeoCampoImportacionRepository
                .findByPlantillaIdAndNombreColumnaOrigenIgnoreCaseAndActivoTrue(plantillaId, nombreColumnaOrigen)
                .filter(m -> mapeoIdExcluido == null || !m.getId().equals(mapeoIdExcluido))
                .isPresent();

        if (duplicado) {
            throw new IllegalArgumentException(
                    "Ya existe un mapeo activo para la columna '" + nombreColumnaOrigen
                            + "' en esta plantilla.");
        }
    }

    private void validarCampoClinicoNoDuplicado(Long plantillaId, Long campoClinicoId, Long mapeoIdExcluido) {
        boolean duplicado = mapeoCampoImportacionRepository
                .findByPlantillaIdAndCampoClinicoIdAndActivoTrue(plantillaId, campoClinicoId)
                .filter(m -> mapeoIdExcluido == null || !m.getId().equals(mapeoIdExcluido))
                .isPresent();

        if (duplicado) {
            throw new IllegalArgumentException(
                    "Ya existe un mapeo activo apuntando a ese campo clínico en esta plantilla.");
        }
    }

    private CampoClinico resolverCampoClinico(PlantillaImportacion plantilla, Long campoClinicoId) {
        CampoClinico campoClinico = campoClinicoRepository.findById(campoClinicoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el campo clínico con id: " + campoClinicoId));

        if (!Boolean.TRUE.equals(campoClinico.getActivo())) {
            throw new IllegalArgumentException(
                    "El campo clínico con id " + campoClinicoId + " está desactivado.");
        }

        if (!campoClinico.getDataset().getId().equals(plantilla.getDataset().getId())) {
            throw new IllegalArgumentException(
                    "El campo clínico no pertenece al mismo dataset que la plantilla.");
        }

        return campoClinico;
    }

    private PoliticaCampoFaltante resolverPolitica(MapeoCampoImportacionRequestDto request) {
        if (request.getPoliticaCampoFaltante() != null) {
            return request.getPoliticaCampoFaltante();
        }

        return PoliticaCampoFaltante.porDefecto(Boolean.TRUE.equals(request.getObligatorio()));
    }

    private PlantillaImportacion obtenerPlantillaOLanzar(Long plantillaId) {
        return plantillaImportacionRepository.findById(plantillaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la plantilla de importación con id: " + plantillaId));
    }

    private PlantillaImportacion obtenerPlantillaActivaOLanzar(Long plantillaId) {
        PlantillaImportacion plantilla = obtenerPlantillaOLanzar(plantillaId);

        if (!Boolean.TRUE.equals(plantilla.getActiva())) {
            throw new IllegalArgumentException(
                    "La plantilla está desactivada; no se pueden crear ni editar mapeos.");
        }

        return plantilla;
    }

    private MapeoCampoImportacion obtenerMapeoOLanzar(Long plantillaId, Long mapeoId) {
        MapeoCampoImportacion mapeo = mapeoCampoImportacionRepository.findById(mapeoId)
                .orElseThrow(() -> new NoSuchElementException("No existe el mapeo con id: " + mapeoId));

        if (!mapeo.getPlantilla().getId().equals(plantillaId)) {
            throw new NoSuchElementException(
                    "El mapeo " + mapeoId + " no pertenece a la plantilla " + plantillaId);
        }

        return mapeo;
    }

    private MapeoCampoImportacionResponseDto mapToDto(MapeoCampoImportacion mapeo) {
        return MapeoCampoImportacionResponseDto.builder()
                .id(mapeo.getId())
                .plantillaId(mapeo.getPlantilla().getId())
                .nombreColumnaOrigen(mapeo.getNombreColumnaOrigen())
                .campoClinicoId(mapeo.getCampoClinico().getId())
                .campoClinicoCodigo(mapeo.getCampoClinico().getCodigo())
                .campoClinicoEtiqueta(mapeo.getCampoClinico().getEtiqueta())
                .tipoDato(mapeo.getTipoDato().name())
                .obligatorio(mapeo.getObligatorio())
                .politicaCampoFaltante(
                        mapeo.getPoliticaCampoFaltante() != null
                                ? mapeo.getPoliticaCampoFaltante().name()
                                : null)
                .valorPorDefecto(mapeo.getValorPorDefecto())
                .orden(mapeo.getOrden())
                .activo(mapeo.getActivo())
                .build();
    }
}
