package com.preventiva.backend;

import com.preventiva.backend.dto.ColumnaReanudacionDto;
import com.preventiva.backend.dto.ColumnasReanudacionResponseDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ReanudarBorradorDatasetDto;
import com.preventiva.backend.entity.ImportacionTrabajo;
import com.preventiva.backend.enums.TipoDatoExcel;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 6.8F.1 — Tests 10, 11 y 12: reanudar un dataset BORRADOR a través de
 * sus distintos estados, reconstruir la revisión de columnas de una copia de
 * trabajo, y descartar un borrador completo sin dejar residuos.
 */
class ReanudacionBorradorIntegrationTest extends AbstractImportacionTrabajoFase68FTest {

    // ---- Test 10: reanudar un borrador a través de sus estados ----

    @Test
    void reanudarBorrador_avanzaDeCorregirFilasAResultadoYLuegoADetalleDataset() {
        Long datasetId = crearDatasetBorrador("REANUDACION");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla reanudacion");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);

        String contenidoCsv = """
                HC;FECHA CIRUGIA
                H001;fecha_mala
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        ReanudarBorradorDatasetDto reanudacion1 = datasetClinicoService.reanudarBorrador(datasetId);
        assertThat(reanudacion1.isPuedeReanudarse()).isTrue();
        assertThat(reanudacion1.getPasoRecomendado()).isEqualTo("CORREGIR_FILAS");
        assertThat(reanudacion1.getImportacionTrabajoId()).isEqualTo(trabajoId);

        importacionTrabajoService.corregirCelda(trabajoId, 2, "FECHA CIRUGIA", "2026-01-01");

        ReanudarBorradorDatasetDto reanudacion2 = datasetClinicoService.reanudarBorrador(datasetId);
        assertThat(reanudacion2.isPuedeReanudarse()).isTrue();
        assertThat(reanudacion2.getImportable()).isTrue();
        // Diseño actual: LISTA_PARA_IMPORTAR también recomienda CORREGIR_FILAS
        // (para que el usuario revise antes de importar), nunca IMPORTAR directo.
        assertThat(reanudacion2.getPasoRecomendado()).isEqualTo("CORREGIR_FILAS");

        importacionTrabajoService.importarDesdeTrabajo(trabajoId);

        ReanudarBorradorDatasetDto reanudacion3 = datasetClinicoService.reanudarBorrador(datasetId);
        assertThat(reanudacion3.isPuedeReanudarse()).isTrue();
        assertThat(reanudacion3.getPasoRecomendado()).isEqualTo("RESULTADO");
        assertThat(reanudacion3.getImportacionGenericaId()).isNotNull();

        datasetClinicoService.activar(datasetId);

        ReanudarBorradorDatasetDto reanudacion4 = datasetClinicoService.reanudarBorrador(datasetId);
        assertThat(reanudacion4.isPuedeReanudarse()).isFalse();
        assertThat(reanudacion4.getPasoRecomendado()).isEqualTo("DETALLE_DATASET");
        assertThat(reanudacion4.getMensaje()).isNotBlank();
    }

    // ---- Test 11: reconstruir columnas de una copia de trabajo ----

    @Test
    void obtenerColumnasReanudacion_reconstruyeColumnasMapeadasYMarcaLasNoReconocidas() {
        Long datasetId = crearDatasetBorrador("COLUMNAS");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla columnas");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);

        // COLUMNA_RARA no está mapeada en la plantilla: debe aparecer como no reconocida.
        String contenidoCsv = """
                HC;FECHA CIRUGIA;COLUMNA_RARA
                H001;2026-01-01;algo
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        ColumnasReanudacionResponseDto columnas = importacionTrabajoService.obtenerColumnasReanudacion(trabajoId);

        assertThat(columnas.getImportacionTrabajoId()).isEqualTo(trabajoId);
        assertThat(columnas.getDatasetId()).isEqualTo(datasetId);
        assertThat(columnas.getPlantillaId()).isEqualTo(plantillaId);
        assertThat(columnas.getMensaje()).isNull();
        assertThat(columnas.getColumnas()).isNotEmpty();

        ColumnaReanudacionDto columnaHc = columnas.getColumnas().stream()
                .filter(c -> "HC".equals(c.getNombreOriginal()))
                .findFirst().orElseThrow();
        assertThat(columnaHc.isMapeada()).isTrue();
        assertThat(columnaHc.isUsar()).isTrue();
        assertThat(columnaHc.getTipoDato()).isEqualTo("TEXTO");
        assertThat(columnaHc.getCampoClinicoCodigo()).isEqualTo("pacienteCodigo");
        assertThat(columnaHc.isObligatorio()).isTrue();

        ColumnaReanudacionDto columnaFecha = columnas.getColumnas().stream()
                .filter(c -> "FECHA CIRUGIA".equals(c.getNombreOriginal()))
                .findFirst().orElseThrow();
        assertThat(columnaFecha.isMapeada()).isTrue();
        assertThat(columnaFecha.getTipoDato()).isEqualTo("FECHA");
        assertThat(columnaFecha.getCampoClinicoCodigo()).isEqualTo("fechaEvento");

        ColumnaReanudacionDto columnaRara = columnas.getColumnas().stream()
                .filter(c -> "COLUMNA_RARA".equals(c.getNombreOriginal()))
                .findFirst().orElseThrow();
        assertThat(columnaRara.isMapeada()).isFalse();
        assertThat(columnaRara.isUsar()).isFalse();
        assertThat(columnaRara.getCampoClinicoCodigo()).isNull();
    }

    @Test
    void obtenerColumnasReanudacion_sinInformacionSuficiente_devuelveMensajeClaro() {
        // OJO: un archivo SIN_FILAS_CLINICAS (cabecera válida, filas de datos
        // vacías) SÍ guarda columnasPresentes -- registrarColumnasPresentes() se
        // ejecuta al procesar la cabecera, independientemente de si luego hay
        // filas de datos reconocibles. Con "HC" en la cabecera y un mapeo real
        // para HC, el endpoint reconstruiría igualmente esa columna (caso A).
        //
        // El caso B ("sin información suficiente") solo se da si la copia no
        // tiene ni columnasPresentes ni errores de columna no reconocida
        // guardados: en la práctica, una copia de trabajo antigua o con datos
        // incompletos (creada antes de que existiera este campo, o corrupta).
        // Se simula manipulando la entidad directamente, en vez de fabricar un
        // CSV que en realidad sí deja información reconstruible.
        Long datasetId = crearDatasetBorrador("COLUMNASVACIAS");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla columnas vacias");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);

        String contenidoCsv = """
                HC
                H001
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        ImportacionTrabajo entidad = importacionTrabajoRepository.findById(trabajoId).orElseThrow();
        entidad.setColumnasPresentes(null);
        entidad.setErroresGlobales(null);
        importacionTrabajoRepository.save(entidad);

        ColumnasReanudacionResponseDto columnas = importacionTrabajoService.obtenerColumnasReanudacion(trabajoId);
        assertThat(columnas.getColumnas()).isEmpty();
        assertThat(columnas.getMensaje()).isEqualTo("No se pueden reconstruir columnas de esta copia.");
    }

    @Test
    void obtenerColumnasReanudacion_conInformacionEnLaCabecera_devuelveColumnasAunSinFilasDeDatos() {
        // Caso A explícito: aunque el archivo no tenga ninguna fila clínica
        // reconocible (SIN_FILAS_CLINICAS), si la cabecera sí tenía columnas
        // mapeadas, columnasPresentes se guardó igual y el endpoint puede -y
        // debe- reconstruir esa columna.
        Long datasetId = crearDatasetBorrador("COLUMNASSINFILAS");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla columnas sin filas");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);

        String contenidoCsv = """
                HC
                ;
                """;
        ImportacionTrabajoResponseDto trabajo = importacionTrabajoService
                .crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo();
        assertThat(trabajo.getTotalFilasLeidas()).isZero();

        ColumnasReanudacionResponseDto columnas = importacionTrabajoService.obtenerColumnasReanudacion(trabajo.getId());
        assertThat(columnas.getMensaje()).isNull();
        assertThat(columnas.getColumnas())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.getNombreOriginal()).isEqualTo("HC");
                    assertThat(c.isMapeada()).isTrue();
                });
    }

    // ---- Test 12: descartar un borrador completo, sin dejar residuos ----

    @Test
    void descartarBorrador_eliminaDatasetYTodasSusDependencias() {
        Long datasetId = crearDatasetBorrador("DESCARTAR");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla descartar");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);

        String contenidoCsv = """
                HC;FECHA CIRUGIA
                H001;fecha_mala
                """;
        ImportacionTrabajoResponseDto trabajo = importacionTrabajoService
                .crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo();
        Long trabajoId = trabajo.getId();
        // Genera al menos un evento (COPIA_CREADA) y una fila, para probar la cascada completa.
        assertThat(eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId))
                .isNotEmpty();
        assertThat(filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajoId))
                .isNotEmpty();

        datasetClinicoService.descartarBorrador(datasetId);

        assertThat(datasetClinicoRepository.findById(datasetId)).isEmpty();
        assertThat(campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)).isEmpty();
        assertThat(plantillaImportacionRepository.findByDatasetId(datasetId)).isEmpty();
        assertThat(mapeoCampoImportacionRepository.findByPlantillaIdAndActivoTrue(plantillaId)).isEmpty();
        assertThat(importacionTrabajoRepository.findByDatasetId(datasetId)).isEmpty();
        assertThat(importacionTrabajoRepository.findById(trabajoId)).isEmpty();
        assertThat(filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajoId))
                .isEmpty();
        assertThat(eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId))
                .isEmpty();

        assertThatThrownBy(() -> datasetClinicoService.reanudarBorrador(datasetId))
                .isInstanceOf(NoSuchElementException.class);
    }
}
