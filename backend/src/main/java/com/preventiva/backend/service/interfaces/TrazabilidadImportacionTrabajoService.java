package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ResumenTrazabilidadImportacionTrabajoDto;
import com.preventiva.backend.dto.TrazabilidadImportacionTrabajoResponseDto;
import com.preventiva.backend.entity.FilaImportacionTrabajo;
import com.preventiva.backend.entity.ImportacionGenerica;
import com.preventiva.backend.entity.ImportacionTrabajo;

import java.util.List;

/**
 * Auditoría de la copia de trabajo: qué se hizo, cuándo y con qué valores.
 * Cada "registrar*" crea un evento de solo lectura; nunca guarda el archivo
 * completo ni todas las filas, solo lo necesario para reconstruir la acción
 * (ver punto 10/11 de la Fase 6.8D.1).
 */
public interface TrazabilidadImportacionTrabajoService {

    /** Par valorAnterior/valorNuevo para los ejemplos limitados de normalizarColumna. */
    record EjemploValor(Integer numeroFila, String valorAnterior, String valorNuevo) {
    }

    // ---- Registro ----

    void registrarCopiaCreada(ImportacionTrabajo trabajo, ImportacionTrabajoResponseDto resumen);

    void registrarCeldaCorregida(
            ImportacionTrabajo trabajo, Integer numeroFila, String columna,
            String valorAnterior, String valorNuevo, ImportacionTrabajoResponseDto resumenResultante);

    void registrarCorreccionCeldaDeshecha(
            ImportacionTrabajo trabajo, Integer numeroFila, String columna,
            String valorAnterior, String valorNuevo, ImportacionTrabajoResponseDto resumenResultante);

    void registrarCorreccionesFilaDeshechas(
            ImportacionTrabajo trabajo, Integer numeroFila, List<String> columnasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante);

    void registrarTodasCorreccionesDeshechas(
            ImportacionTrabajo trabajo, int totalCorreccionesEliminadas, int filasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante);

    void registrarFilaExcluida(
            ImportacionTrabajo trabajo, FilaImportacionTrabajo fila, ImportacionTrabajoResponseDto resumenResultante);

    void registrarFilaIncluida(
            ImportacionTrabajo trabajo, Integer numeroFila, ImportacionTrabajoResponseDto resumenResultante);

    void registrarFilasSimilaresExcluidas(
            ImportacionTrabajo trabajo, String tipoError, String nombreColumna, List<Integer> filasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante);

    void registrarTodasExclusionesDeshechas(
            ImportacionTrabajo trabajo, int totalFilasReincluidas, ImportacionTrabajoResponseDto resumenResultante);

    void registrarCopiaRestauradaOriginal(
            ImportacionTrabajo trabajo, int totalCorreccionesEliminadas, int totalExclusionesEliminadas,
            int totalFilasAfectadas, ImportacionTrabajoResponseDto resumenResultante);

    void registrarColumnaRellenada(
            ImportacionTrabajo trabajo, String nombreColumna, String valorNuevo, String tipoErrorFiltrado,
            boolean soloFilasConEsteProblema, List<Integer> filasAfectadas,
            ImportacionTrabajoResponseDto resumenResultante);

    void registrarColumnaNormalizada(
            ImportacionTrabajo trabajo, String nombreColumna, String estrategia, List<Integer> filasAfectadas,
            List<EjemploValor> ejemplos, ImportacionTrabajoResponseDto resumenResultante);

    void registrarRevalidacion(ImportacionTrabajo trabajo, ImportacionTrabajoResponseDto resumenResultante);

    void registrarImportacionRealizada(
            ImportacionTrabajo trabajo, ImportacionGenerica importacionGenerica,
            int filasImportadas, int filasExcluidas, int totalAdvertencias);

    void registrarDatasetActivado(
            ImportacionTrabajo trabajo, Long datasetId, String estadoAnterior, String estadoNuevo);

    void registrarCopiaDescartada(ImportacionTrabajo trabajo);

    // ---- Consulta ----

    TrazabilidadImportacionTrabajoResponseDto obtenerTrazabilidad(Long importacionTrabajoId);

    ResumenTrazabilidadImportacionTrabajoDto obtenerResumen(Long importacionTrabajoId);

    TrazabilidadImportacionTrabajoResponseDto obtenerTrazabilidadPorImportacionGenerica(Long importacionGenericaId);
}
