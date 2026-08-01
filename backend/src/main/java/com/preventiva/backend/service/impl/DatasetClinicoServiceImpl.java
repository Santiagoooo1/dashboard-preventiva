package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ReanudarBorradorDatasetDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.entity.Hospital;
import com.preventiva.backend.entity.ImportacionGenerica;
import com.preventiva.backend.entity.ImportacionTrabajo;
import com.preventiva.backend.entity.PlantillaImportacion;
import com.preventiva.backend.enums.EstadoDatasetClinico;
import com.preventiva.backend.enums.EstadoImportacionTrabajo;
import com.preventiva.backend.enums.PasoRecomendadoReanudacion;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.repository.ErrorImportacionGenericaRepository;
import com.preventiva.backend.repository.EventoImportacionTrabajoRepository;
import com.preventiva.backend.repository.HospitalRepository;
import com.preventiva.backend.repository.ImportacionGenericaRepository;
import com.preventiva.backend.repository.ImportacionTrabajoRepository;
import com.preventiva.backend.repository.FilaImportacionTrabajoRepository;
import com.preventiva.backend.repository.MapeoCampoImportacionRepository;
import com.preventiva.backend.repository.PlantillaImportacionRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.ImportacionTrabajoService;
import com.preventiva.backend.service.interfaces.TrazabilidadImportacionTrabajoService;
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
    private final EventoImportacionTrabajoRepository eventoImportacionTrabajoRepository;
    private final TrazabilidadImportacionTrabajoService trazabilidadService;
    private final ImportacionTrabajoService importacionTrabajoService;

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
        String estadoAnterior = dataset.getEstadoDataset() != null
                ? dataset.getEstadoDataset().name()
                : EstadoDatasetClinico.ACTIVO.name();
        dataset.setEstadoDataset(EstadoDatasetClinico.ACTIVO);
        DatasetClinicoResponseDto resultado = mapToDto(datasetClinicoRepository.save(dataset));

        // Solo se registra si hay una copia de trabajo que efectivamente
        // terminó en importación: si el dataset se activó por otra vía (o no
        // hay ninguna ImportacionTrabajo asociada), no se fuerza el evento.
        importacionTrabajoRepository.findByDatasetId(id).stream()
                .filter(t -> t.getEstado() == com.preventiva.backend.enums.EstadoImportacionTrabajo.IMPORTADA)
                .findFirst()
                .ifPresent(trabajo -> trazabilidadService.registrarDatasetActivado(
                        trabajo, id, estadoAnterior, EstadoDatasetClinico.ACTIVO.name()));

        return resultado;
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

        // Decisión (Fase 6.8D.1): al borrar el borrador en cascada, sus eventos
        // de trazabilidad también se borran. No tiene sentido conservar un
        // evento BORRADOR_DESCARTADO que desaparecería en la misma
        // transacción, así que ese tipo de evento no se registra aquí.
        List<ImportacionTrabajo> trabajos = importacionTrabajoRepository.findByDatasetId(id);
        if (!trabajos.isEmpty()) {
            List<Long> idsTrabajos = trabajos.stream().map(ImportacionTrabajo::getId).toList();
            eventoImportacionTrabajoRepository.deleteByImportacionTrabajoIdIn(idsTrabajos);
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

    private static final String MENSAJE_YA_ACTIVO = "Este dataset ya está activo. Puedes consultarlo desde su detalle.";
    private static final String MENSAJE_NO_REANUDABLE =
            "Este dataset no está en estado de borrador y no puede reanudarse.";

    @Override
    public ReanudarBorradorDatasetDto reanudarBorrador(Long id) {
        DatasetClinico dataset = obtenerDatasetOLanzar(id);
        EstadoDatasetClinico estado = dataset.getEstadoDataset() != null
                ? dataset.getEstadoDataset()
                : EstadoDatasetClinico.ACTIVO;

        ReanudarBorradorDatasetDto.ReanudarBorradorDatasetDtoBuilder base = ReanudarBorradorDatasetDto.builder()
                .datasetId(dataset.getId())
                .codigo(dataset.getCodigo())
                .nombre(dataset.getNombre())
                .estadoDataset(estado.name());

        if (estado == EstadoDatasetClinico.ACTIVO) {
            return base.puedeReanudarse(false)
                    .motivoNoReanudable(MENSAJE_YA_ACTIVO)
                    .pasoRecomendado(PasoRecomendadoReanudacion.DETALLE_DATASET.name())
                    .mensaje(MENSAJE_YA_ACTIVO)
                    .build();
        }

        if (estado != EstadoDatasetClinico.BORRADOR && estado != EstadoDatasetClinico.VALIDANDO) {
            return base.puedeReanudarse(false)
                    .motivoNoReanudable(MENSAJE_NO_REANUDABLE)
                    .pasoRecomendado(PasoRecomendadoReanudacion.DETALLE_DATASET.name())
                    .mensaje(MENSAJE_NO_REANUDABLE)
                    .build();
        }

        // BORRADOR/VALIDANDO: busca la copia de trabajo más relevante para reanudar.
        // No se consideran útiles las copias DESCARTADA: no reflejan nada que el
        // usuario pueda seguir corrigiendo.
        List<ImportacionTrabajo> trabajos = importacionTrabajoRepository.findByDatasetIdOrderByFechaCreacionDesc(id);
        ImportacionTrabajo trabajo = trabajos.stream()
                .filter(t -> t.getEstado() == EstadoImportacionTrabajo.EN_EDICION)
                .findFirst()
                .or(() -> trabajos.stream()
                        .filter(t -> t.getEstado() == EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR)
                        .findFirst())
                .or(() -> trabajos.stream()
                        .filter(t -> t.getEstado() == EstadoImportacionTrabajo.IMPORTADA)
                        .findFirst())
                .orElse(null);

        if (trabajo != null) {
            return construirReanudacionConTrabajo(base, trabajo);
        }

        // Sin copia de trabajo útil: ¿hay al menos una plantilla o campos ya
        // definidos? Entonces se puede volver a revisar columnas; si no, no hay
        // nada más que hacer que subir un archivo. Si ya existía alguna copia
        // (aunque quedara DESCARTADA), se avisa de eso en vez de dar a entender
        // que el borrador nunca se ha tocado.
        boolean huboCopiaDescartada = !trabajos.isEmpty();

        List<PlantillaImportacion> plantillasActivas = plantillaImportacionRepository.findByDatasetIdAndActivaTrue(id);
        List<PlantillaImportacion> plantillas =
                !plantillasActivas.isEmpty() ? plantillasActivas : plantillaImportacionRepository.findByDatasetId(id);
        List<CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(id);

        if (!plantillas.isEmpty() || !campos.isEmpty()) {
            String mensaje = "Este borrador tiene columnas configuradas pero ninguna importación en curso.";
            return base.puedeReanudarse(true)
                    .pasoRecomendado(PasoRecomendadoReanudacion.COLUMNAS.name())
                    .plantillaId(plantillas.isEmpty() ? null : plantillas.get(0).getId())
                    .huboCopiaDescartada(huboCopiaDescartada)
                    .mensaje(mensaje)
                    .build();
        }

        String mensaje = "No hay ninguna importación en curso para este borrador. Puedes subir un archivo para continuar.";
        return base.puedeReanudarse(true)
                .pasoRecomendado(PasoRecomendadoReanudacion.SUBIR_ARCHIVO.name())
                .huboCopiaDescartada(huboCopiaDescartada)
                .mensaje(mensaje)
                .build();
    }

    private ReanudarBorradorDatasetDto construirReanudacionConTrabajo(
            ReanudarBorradorDatasetDto.ReanudarBorradorDatasetDtoBuilder base, ImportacionTrabajo trabajo) {
        EstadoImportacionTrabajo estadoTrabajo = trabajo.getEstado();
        Long importacionGenericaId =
                trabajo.getImportacionGenerica() != null ? trabajo.getImportacionGenerica().getId() : null;
        // Los totales (errores, advertencias, importable...) no viven directamente
        // en la entidad: se recalculan a partir de las filas, igual que en el resto
        // de endpoints de la copia de trabajo. Se reutiliza ese cálculo en vez de
        // duplicarlo aquí.
        ImportacionTrabajoResponseDto resumenTrabajo = importacionTrabajoService.obtenerPorId(trabajo.getId());

        base.importacionTrabajoId(trabajo.getId())
                .plantillaId(trabajo.getPlantilla().getId())
                .importacionGenericaId(importacionGenericaId)
                .totalFilasLeidas(resumenTrabajo.getTotalFilasLeidas())
                .totalErrores(resumenTrabajo.getTotalErrores())
                .totalAdvertencias(resumenTrabajo.getTotalAdvertencias())
                .totalFilasExcluidas(resumenTrabajo.getTotalFilasExcluidas())
                .importable(resumenTrabajo.getImportable())
                .estadoImportacionTrabajo(estadoTrabajo.name());

        if (estadoTrabajo == EstadoImportacionTrabajo.EN_EDICION) {
            return base.puedeReanudarse(true)
                    .pasoRecomendado(PasoRecomendadoReanudacion.CORREGIR_FILAS.name())
                    .mensaje("Hay una copia interna con correcciones pendientes. Continúa corrigiendo antes de importar.")
                    .build();
        }

        if (estadoTrabajo == EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR) {
            return base.puedeReanudarse(true)
                    .pasoRecomendado(PasoRecomendadoReanudacion.CORREGIR_FILAS.name())
                    .mensaje("La copia interna está lista para importar. Revísala antes de continuar.")
                    .build();
        }

        // IMPORTADA: la copia de trabajo ya se usó, el dataset debería seguir en
        // BORRADOR/VALIDANDO solo si la activación posterior no llegó a completarse.
        if (importacionGenericaId != null) {
            return base.puedeReanudarse(true)
                    .pasoRecomendado(PasoRecomendadoReanudacion.RESULTADO.name())
                    .mensaje("Esta copia interna ya se importó. Puedes ver el resultado de la importación.")
                    .build();
        }
        return base.puedeReanudarse(true)
                .pasoRecomendado(PasoRecomendadoReanudacion.DETALLE_DATASET.name())
                .mensaje("Esta copia interna ya se importó. Consulta el detalle del dataset.")
                .build();
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
