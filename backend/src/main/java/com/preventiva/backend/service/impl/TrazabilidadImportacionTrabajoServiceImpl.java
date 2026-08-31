package com.preventiva.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.EventoImportacionTrabajoDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ResumenTrazabilidadImportacionTrabajoDto;
import com.preventiva.backend.dto.TrazabilidadImportacionTrabajoResponseDto;
import com.preventiva.backend.entity.EventoImportacionTrabajo;
import com.preventiva.backend.entity.FilaImportacionTrabajo;
import com.preventiva.backend.entity.ImportacionGenerica;
import com.preventiva.backend.entity.ImportacionTrabajo;
import com.preventiva.backend.enums.EstadoImportacionTrabajo;
import com.preventiva.backend.enums.TipoEventoImportacionTrabajo;
import com.preventiva.backend.repository.EventoImportacionTrabajoRepository;
import com.preventiva.backend.repository.FilaImportacionTrabajoRepository;
import com.preventiva.backend.repository.ImportacionTrabajoRepository;
import com.preventiva.backend.service.interfaces.TrazabilidadImportacionTrabajoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Auditoría de la copia de trabajo (Fase 6.8D.1). Cada método "registrar*"
 * guarda un {@link EventoImportacionTrabajo} de solo lectura con un detalle
 * JSON acotado (nunca el archivo completo ni todas las filas).
 */
@Service
@RequiredArgsConstructor
public class TrazabilidadImportacionTrabajoServiceImpl implements TrazabilidadImportacionTrabajoService {

    private static final String ACTOR_POR_DEFECTO = "usuario-local";
    // Límite defensivo: nunca guardar el listado completo de filas afectadas
    // en el detalle de un evento (punto 10 del encargo), solo un resumen.
    private static final int MAXIMO_FILAS_EN_DETALLE = 20;

    private final EventoImportacionTrabajoRepository eventoRepository;
    private final ImportacionTrabajoRepository importacionTrabajoRepository;
    private final FilaImportacionTrabajoRepository filaImportacionTrabajoRepository;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    @Override
    public void registrarCopiaCreada(ImportacionTrabajo trabajo, ImportacionTrabajoResponseDto resumen) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("nombreArchivoOriginal", trabajo.getNombreArchivoOriginal());
        detalle.put("hashArchivoOriginal", trabajo.getHashArchivoOriginal());
        detalle.put("datasetId", trabajo.getDataset().getId());
        detalle.put("plantillaId", trabajo.getPlantilla().getId());
        detalle.put("totalFilasLeidas", resumen.getTotalFilasLeidas());
        detalle.put("totalErrores", resumen.getTotalErrores());
        detalle.put("totalAdvertencias", resumen.getTotalAdvertencias());
        detalle.put("importable", resumen.getImportable());
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.COPIA_CREADA, null, null, null, null, detalle);
    }

    @Override
    public void registrarCeldaCorregida(
            ImportacionTrabajo trabajo, Integer numeroFila, String columna,
            String valorAnterior, String valorNuevo, ImportacionTrabajoResponseDto resumenResultante) {
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.CELDA_CORREGIDA, numeroFila, columna,
                valorAnterior, valorNuevo, detalleResumen(resumenResultante));
    }

    @Override
    public void registrarCorreccionCeldaDeshecha(
            ImportacionTrabajo trabajo, Integer numeroFila, String columna,
            String valorAnterior, String valorNuevo, ImportacionTrabajoResponseDto resumenResultante) {
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.CORRECCION_CELDA_DESHECHA, numeroFila, columna,
                valorAnterior, valorNuevo, detalleResumen(resumenResultante));
    }

    @Override
    public void registrarCorreccionesFilaDeshechas(
            ImportacionTrabajo trabajo, Integer numeroFila, List<String> columnasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("columnasAfectadas", columnasAfectadas);
        detalle.put("totalCorreccionesEliminadas", columnasAfectadas.size());
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.CORRECCIONES_FILA_DESHECHAS, numeroFila, null,
                null, null, detalle);
    }

    @Override
    public void registrarTodasCorreccionesDeshechas(
            ImportacionTrabajo trabajo, int totalCorreccionesEliminadas, int filasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("totalCorreccionesEliminadas", totalCorreccionesEliminadas);
        detalle.put("filasAfectadas", filasAfectadas);
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.TODAS_CORRECCIONES_DESHECHAS, null, null,
                null, null, detalle);
    }

    @Override
    public void registrarFilaExcluida(
            ImportacionTrabajo trabajo, FilaImportacionTrabajo fila, ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("erroresActuales", resumenErrores(fila));
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.FILA_EXCLUIDA, fila.getNumeroFilaOriginal(), null,
                null, null, detalle);
    }

    @Override
    public void registrarFilaIncluida(
            ImportacionTrabajo trabajo, Integer numeroFila, ImportacionTrabajoResponseDto resumenResultante) {
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.FILA_INCLUIDA, numeroFila, null, null, null,
                detalleResumen(resumenResultante));
    }

    @Override
    public void registrarFilasSimilaresExcluidas(
            ImportacionTrabajo trabajo, String tipoError, String nombreColumna, List<Integer> filasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("tipoError", tipoError);
        detalle.put("criterio", "mismo tipoError + misma columna");
        detalle.put("totalAfectadas", filasAfectadas.size());
        detalle.put("filasAfectadas", limitarFilas(filasAfectadas));
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.FILAS_SIMILARES_EXCLUIDAS, null, nombreColumna,
                null, null, detalle);
    }

    @Override
    public void registrarTodasExclusionesDeshechas(
            ImportacionTrabajo trabajo, int totalFilasReincluidas, ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("totalFilasReincluidas", totalFilasReincluidas);
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.TODAS_EXCLUSIONES_DESHECHAS, null, null, null, null,
                detalle);
    }

    @Override
    public void registrarCopiaRestauradaOriginal(
            ImportacionTrabajo trabajo, int totalCorreccionesEliminadas, int totalExclusionesEliminadas,
            int totalFilasAfectadas, ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("totalCorreccionesEliminadas", totalCorreccionesEliminadas);
        detalle.put("totalExclusionesEliminadas", totalExclusionesEliminadas);
        detalle.put("totalFilasAfectadas", totalFilasAfectadas);
        detalle.put("hashArchivoOriginal", trabajo.getHashArchivoOriginal());
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.COPIA_RESTAURADA_ORIGINAL, null, null, null, null,
                detalle);
    }

    @Override
    public void registrarColumnaRellenada(
            ImportacionTrabajo trabajo, String nombreColumna, String valorNuevo, String tipoErrorFiltrado,
            boolean soloFilasConEsteProblema, List<Integer> filasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("tipoErrorFiltrado", tipoErrorFiltrado);
        detalle.put("soloFilasConEsteProblema", soloFilasConEsteProblema);
        detalle.put("totalFilasAfectadas", filasAfectadas.size());
        detalle.put("filasAfectadas", limitarFilas(filasAfectadas));
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.COLUMNA_RELLENADA, null, nombreColumna, null,
                valorNuevo, detalle);
    }

    @Override
    public void registrarColumnaNormalizada(
            ImportacionTrabajo trabajo, String nombreColumna, String estrategia, List<Integer> filasAfectadas,
            List<EjemploValor> ejemplos, ImportacionTrabajoResponseDto resumenResultante) {
        Map<String, Object> detalle = detalleResumen(resumenResultante);
        detalle.put("estrategia", estrategia);
        detalle.put("totalFilasAfectadas", filasAfectadas.size());
        detalle.put("filasAfectadas", limitarFilas(filasAfectadas));
        detalle.put("ejemplos", ejemplos.stream()
                .limit(3)
                .map(e -> Map.of(
                        "numeroFila", e.numeroFila(),
                        "valorAnterior", e.valorAnterior() == null ? "" : e.valorAnterior(),
                        "valorNuevo", e.valorNuevo() == null ? "" : e.valorNuevo()))
                .toList());
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.COLUMNA_NORMALIZADA, null, nombreColumna, null, null,
                detalle);
    }

    @Override
    public void registrarRevalidacion(ImportacionTrabajo trabajo, ImportacionTrabajoResponseDto resumenResultante) {
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.REVALIDACION_EJECUTADA, null, null, null, null,
                detalleResumen(resumenResultante));
    }

    @Override
    public void registrarImportacionRealizada(
            ImportacionTrabajo trabajo, ImportacionGenerica importacionGenerica,
            int filasImportadas, int filasExcluidas, int totalAdvertencias) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("importacionGenericaId", importacionGenerica.getId());
        detalle.put("datasetId", trabajo.getDataset().getId());
        detalle.put("filasImportadas", filasImportadas);
        detalle.put("filasExcluidas", filasExcluidas);
        detalle.put("totalAdvertencias", totalAdvertencias);
        detalle.put("estadoFinal", importacionGenerica.getEstado().name());
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.IMPORTACION_REALIZADA, null, null, null, null, detalle);
    }

    @Override
    public void registrarDatasetActivado(
            ImportacionTrabajo trabajo, Long datasetId, String estadoAnterior, String estadoNuevo) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("datasetId", datasetId);
        detalle.put("estadoAnterior", estadoAnterior);
        detalle.put("estadoNuevo", estadoNuevo);
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.DATASET_ACTIVADO, null, null, null, null, detalle);
    }

    @Override
    public void registrarCopiaDescartada(ImportacionTrabajo trabajo) {
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.COPIA_DESCARTADA, null, null, null, null, null);
    }

    @Override
    public void registrarCopiaRecuperada(ImportacionTrabajo trabajo, String estadoNuevo) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoNuevo", estadoNuevo);
        registrarEvento(trabajo, TipoEventoImportacionTrabajo.COPIA_RECUPERADA, null, null, null, null, detalle);
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TrazabilidadImportacionTrabajoResponseDto obtenerTrazabilidad(Long importacionTrabajoId) {
        return construirRespuesta(obtenerTrabajoOLanzar(importacionTrabajoId));
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenTrazabilidadImportacionTrabajoDto obtenerResumen(Long importacionTrabajoId) {
        ImportacionTrabajo trabajo = obtenerTrabajoOLanzar(importacionTrabajoId);
        List<EventoImportacionTrabajo> eventos =
                eventoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajo.getId());
        return construirResumenTrazabilidad(trabajo, eventos);
    }

    @Override
    @Transactional(readOnly = true)
    public TrazabilidadImportacionTrabajoResponseDto obtenerTrazabilidadPorImportacionGenerica(
            Long importacionGenericaId) {
        ImportacionTrabajo trabajo = importacionTrabajoRepository.findByImportacionGenericaId(importacionGenericaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No hay copia de trabajo asociada a la importación genérica " + importacionGenericaId));
        return construirRespuesta(trabajo);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TrazabilidadImportacionTrabajoResponseDto construirRespuesta(ImportacionTrabajo trabajo) {
        List<EventoImportacionTrabajo> eventos =
                eventoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajo.getId());
        return TrazabilidadImportacionTrabajoResponseDto.builder()
                .resumen(construirResumenTrazabilidad(trabajo, eventos))
                .eventos(eventos.stream().map(this::mapEvento).toList())
                .build();
    }

    private ResumenTrazabilidadImportacionTrabajoDto construirResumenTrazabilidad(
            ImportacionTrabajo trabajo, List<EventoImportacionTrabajo> eventos) {
        int correccionesManuales = 0;
        int correccionesEnBloque = 0;
        int normalizaciones = 0;
        int exclusiones = 0;
        int restauraciones = 0;

        for (EventoImportacionTrabajo evento : eventos) {
            switch (evento.getTipoEvento()) {
                case CELDA_CORREGIDA -> correccionesManuales++;
                case COLUMNA_RELLENADA -> correccionesEnBloque++;
                case COLUMNA_NORMALIZADA -> normalizaciones++;
                case FILA_EXCLUIDA, FILAS_SIMILARES_EXCLUIDAS -> exclusiones++;
                case COPIA_RESTAURADA_ORIGINAL -> restauraciones++;
                default -> {
                    // El resto de tipos de evento no alimenta ningún contador del resumen.
                }
            }
        }

        Contadores contadores = contarErroresYExcluidas(trabajo);

        return ResumenTrazabilidadImportacionTrabajoDto.builder()
                .importacionTrabajoId(trabajo.getId())
                .importacionGenericaId(trabajo.getImportacionGenerica() != null
                        ? trabajo.getImportacionGenerica().getId() : null)
                .datasetId(trabajo.getDataset() != null ? trabajo.getDataset().getId() : null)
                .plantillaId(trabajo.getPlantilla() != null ? trabajo.getPlantilla().getId() : null)
                .nombreArchivoOriginal(trabajo.getNombreArchivoOriginal())
                .hashArchivoOriginal(trabajo.getHashArchivoOriginal())
                .estado(trabajo.getEstado() != null ? trabajo.getEstado().name() : null)
                .totalFilasLeidas(trabajo.getTotalFilasLeidas())
                .totalFilasExcluidas(contadores.filasExcluidas())
                .totalErrores(contadores.totalErrores())
                .totalAdvertencias(contadores.totalAdvertencias())
                .importable(trabajo.getEstado() == EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR)
                .totalEventos(eventos.size())
                .totalCorreccionesManuales(correccionesManuales)
                .totalCorreccionesEnBloque(correccionesEnBloque)
                .totalNormalizaciones(normalizaciones)
                .totalExclusiones(exclusiones)
                .totalRestauraciones(restauraciones)
                .fechaCreacion(trabajo.getFechaCreacion())
                .fechaUltimaRevalidacion(trabajo.getFechaUltimaRevalidacion())
                .build();
    }

    /**
     * Mismo criterio de conteo que ImportacionTrabajoServiceImpl.construirResumen:
     * se duplica aquí (en vez de depender de ese servicio) para evitar un ciclo
     * de beans, ya que ImportacionTrabajoServiceImpl ya depende de este servicio
     * para registrar eventos.
     */
    private Contadores contarErroresYExcluidas(ImportacionTrabajo trabajo) {
        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajo.getId());

        int totalErrores = 0;
        int totalAdvertencias = 0;
        int filasExcluidas = 0;

        if (trabajo.getErroresGlobales() != null) {
            for (ErrorImportacionTrabajoDto error : trabajo.getErroresGlobales()) {
                if ("ERROR".equals(error.getSeveridad())) {
                    totalErrores++;
                } else {
                    totalAdvertencias++;
                }
            }
        }

        for (FilaImportacionTrabajo fila : filas) {
            if (Boolean.TRUE.equals(fila.getExcluida())) {
                filasExcluidas++;
                continue;
            }
            if (fila.getErroresActuales() == null) {
                continue;
            }
            for (ErrorImportacionTrabajoDto error : fila.getErroresActuales()) {
                if ("ERROR".equals(error.getSeveridad())) {
                    totalErrores++;
                } else {
                    totalAdvertencias++;
                }
            }
        }

        return new Contadores(totalErrores, totalAdvertencias, filasExcluidas);
    }

    private record Contadores(int totalErrores, int totalAdvertencias, int filasExcluidas) {
    }

    private Map<String, Object> detalleResumen(ImportacionTrabajoResponseDto resumen) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        if (resumen != null) {
            detalle.put("estadoResultante", resumen.getEstado());
            detalle.put("totalErroresResultante", resumen.getTotalErrores());
            detalle.put("totalAdvertenciasResultante", resumen.getTotalAdvertencias());
            detalle.put("importableResultante", resumen.getImportable());
        }
        return detalle;
    }

    private List<Map<String, Object>> resumenErrores(FilaImportacionTrabajo fila) {
        if (fila.getErroresActuales() == null) {
            return List.of();
        }
        return fila.getErroresActuales().stream()
                .map(e -> Map.<String, Object>of(
                        "nombreColumna", e.getNombreColumna() == null ? "" : e.getNombreColumna(),
                        "tipoError", e.getTipoError() == null ? "" : e.getTipoError(),
                        "severidad", e.getSeveridad() == null ? "" : e.getSeveridad()))
                .toList();
    }

    private List<Integer> limitarFilas(List<Integer> filas) {
        return filas.size() <= MAXIMO_FILAS_EN_DETALLE ? filas : filas.subList(0, MAXIMO_FILAS_EN_DETALLE);
    }

    private void registrarEvento(
            ImportacionTrabajo trabajo, TipoEventoImportacionTrabajo tipoEvento, Integer numeroFila,
            String nombreColumna, String valorAnterior, String valorNuevo, Map<String, Object> detalle) {
        String detalleJson = null;
        if (detalle != null && !detalle.isEmpty()) {
            try {
                detalleJson = objectMapper.writeValueAsString(detalle);
            } catch (Exception e) {
                detalleJson = "{}";
            }
        }

        eventoRepository.save(EventoImportacionTrabajo.builder()
                .importacionTrabajo(trabajo)
                .tipoEvento(tipoEvento)
                .numeroFilaOriginal(numeroFila)
                .nombreColumna(nombreColumna)
                .valorAnterior(valorAnterior)
                .valorNuevo(valorNuevo)
                .detalle(detalleJson)
                .fechaEvento(LocalDateTime.now())
                .actor(ACTOR_POR_DEFECTO)
                .build());
    }

    private EventoImportacionTrabajoDto mapEvento(EventoImportacionTrabajo evento) {
        return EventoImportacionTrabajoDto.builder()
                .id(evento.getId())
                .tipoEvento(evento.getTipoEvento().name())
                .numeroFilaOriginal(evento.getNumeroFilaOriginal())
                .nombreColumna(evento.getNombreColumna())
                .valorAnterior(evento.getValorAnterior())
                .valorNuevo(evento.getValorNuevo())
                .detalle(evento.getDetalle())
                .fechaEvento(evento.getFechaEvento())
                .actor(evento.getActor())
                .build();
    }

    private ImportacionTrabajo obtenerTrabajoOLanzar(Long id) {
        return importacionTrabajoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe la copia de trabajo con id: " + id));
    }
}
