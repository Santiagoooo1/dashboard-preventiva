package com.preventiva.backend;

import com.preventiva.backend.dto.EventoImportacionTrabajoDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ResumenTrazabilidadImportacionTrabajoDto;
import com.preventiva.backend.dto.TrazabilidadImportacionTrabajoResponseDto;
import com.preventiva.backend.enums.TipoDatoExcel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6.8F.1 — Test 9: la trazabilidad registra, en orden, toda la
 * secuencia de acciones sobre una copia de trabajo (creación, corrección,
 * exclusión, corrección en bloque, restauración y, tras corregir de nuevo,
 * la importación final).
 */
class ImportacionTrabajoTrazabilidadIntegrationTest extends AbstractImportacionTrabajoFase68FTest {

    @Test
    void trazabilidad_registraSecuenciaCompletaDeAccionesEnOrden() {
        Long datasetId = crearDatasetBorrador("TRAZABILIDAD");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long campoMicroor = crearCampo(datasetId, "microor", "MICROOR.", TipoDatoExcel.TEXTO, false);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla trazabilidad");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);
        crearMapeo(plantillaId, "MICROOR.", campoMicroor, TipoDatoExcel.TEXTO, false);

        String contenidoCsv = """
                HC;FECHA CIRUGIA;MICROOR.
                H001;fecha_mala;
                H002;2026-01-02;
                H003;2026-01-03;Ecoli
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();
        String hashOriginal = importacionTrabajoService.obtenerPorId(trabajoId).getHashArchivoOriginal();

        // 1. CELDA_CORREGIDA: corrige la fecha inválida de la fila 2.
        importacionTrabajoService.corregirCelda(trabajoId, 2, "FECHA CIRUGIA", "2026-01-01");
        // 2. FILA_EXCLUIDA: excluye la fila 3.
        importacionTrabajoService.actualizarExclusion(trabajoId, 3, true);
        // 3. COLUMNA_RELLENADA: rellena MICROOR. en las filas activas con ese hueco (la 2).
        importacionTrabajoService.rellenarColumna(
                trabajoId, "MICROOR.", "VALOR_OBLIGATORIO_VACIO", "No informado", true);
        // 4. COPIA_RESTAURADA_ORIGINAL: deshace todo lo anterior de golpe.
        importacionTrabajoService.restaurarOriginal(trabajoId);
        // 5. CELDA_CORREGIDA (de nuevo, porque restaurar devolvió el error de fecha).
        importacionTrabajoService.corregirCelda(trabajoId, 2, "FECHA CIRUGIA", "2026-01-01");

        ImportacionTrabajoResponseDto trasCorregir = importacionTrabajoService.obtenerPorId(trabajoId);
        assertThat(trasCorregir.getImportable()).isTrue();

        // 6. IMPORTACION_REALIZADA.
        importacionTrabajoService.importarDesdeTrabajo(trabajoId);

        TrazabilidadImportacionTrabajoResponseDto trazabilidad = trazabilidadService.obtenerTrazabilidad(trabajoId);
        ResumenTrazabilidadImportacionTrabajoDto resumen = trazabilidad.getResumen();
        List<EventoImportacionTrabajoDto> eventos = trazabilidad.getEventos();

        assertThat(resumen.getImportacionTrabajoId()).isEqualTo(trabajoId);
        assertThat(resumen.getHashArchivoOriginal()).isEqualTo(hashOriginal);
        assertThat(resumen.getTotalEventos()).isEqualTo(eventos.size());
        assertThat(eventos).hasSize(7);

        List<String> tipos = eventos.stream().map(EventoImportacionTrabajoDto::getTipoEvento).toList();
        assertThat(tipos).containsExactly(
                "COPIA_CREADA",
                "CELDA_CORREGIDA",
                "FILA_EXCLUIDA",
                "COLUMNA_RELLENADA",
                "COPIA_RESTAURADA_ORIGINAL",
                "CELDA_CORREGIDA",
                "IMPORTACION_REALIZADA");

        // Los eventos deben venir en orden cronológico ascendente.
        List<LocalDateTime> fechas = eventos.stream().map(EventoImportacionTrabajoDto::getFechaEvento).toList();
        assertThat(fechas).isSorted();

        EventoImportacionTrabajoDto primeraCorreccion = eventos.stream()
                .filter(e -> "CELDA_CORREGIDA".equals(e.getTipoEvento()))
                .findFirst().orElseThrow();
        assertThat(primeraCorreccion.getNombreColumna()).isEqualTo("FECHA CIRUGIA");
        assertThat(primeraCorreccion.getValorAnterior()).isEqualTo("fecha_mala");
        assertThat(primeraCorreccion.getValorNuevo()).isEqualTo("2026-01-01");

        // El resumen consultado por separado debe coincidir con el de la trazabilidad completa.
        ResumenTrazabilidadImportacionTrabajoDto resumenSolo = trazabilidadService.obtenerResumen(trabajoId);
        assertThat(resumenSolo.getTotalEventos()).isEqualTo(resumen.getTotalEventos());
    }
}
