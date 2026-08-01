package com.preventiva.backend;

import com.preventiva.backend.dto.CrearImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ErrorImportacionTrabajoDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ImportarDesdeTrabajoResponseDto;
import com.preventiva.backend.dto.RevalidarImportacionTrabajoResponseDto;
import com.preventiva.backend.entity.EventoImportacionTrabajo;
import com.preventiva.backend.entity.FilaImportacionTrabajo;
import com.preventiva.backend.entity.ImportacionTrabajo;
import com.preventiva.backend.enums.TipoDatoExcel;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6.8F.1 — cubre el camino crítico de la copia interna de trabajo:
 * creación, detección de errores, corrección de celda, bloqueo de tipado
 * inválido, exclusión de filas, corrección en bloque (rellenar/normalizar),
 * restauración, importación, activación del dataset y el caso
 * SIN_FILAS_CLINICAS. Cada test es autocontenido (crea su propio dataset) y
 * se revierte solo gracias a {@code @Transactional} en la clase base.
 */
class ImportacionTrabajoIntegrationTest extends AbstractImportacionTrabajoFase68FTest {

    // ---- Test 1: crear copia interna y detectar errores ----

    @Test
    void crearCopiaInterna_detectaErroresDeLasFilas() {
        Long datasetId = crearDatasetBorrador("IMPORTACION");
        Long plantillaId = crearPlantillaConCamposHabituales(datasetId);

        String contenidoCsv = """
                HC;FECHA CIRUGIA;EDAD;MICROOR.
                H001;01/01/2026;45;Staphylococcus
                H002;fecha_mala;40;Ecoli
                ;03/01/2026;30;Proteus
                H004;04/01/2026;abc;Klebsiella
                H005;05/01/2026;28;
                """;
        MockMultipartFile archivo = archivoCsv(contenidoCsv);

        CrearImportacionTrabajoResponseDto creado =
                importacionTrabajoService.crear(archivo, plantillaId, 0, 0);
        ImportacionTrabajoResponseDto trabajo = creado.getImportacionTrabajo();

        assertThat(trabajo.getEstado()).isEqualTo("EN_EDICION");
        assertThat(trabajo.getTotalFilasLeidas()).isEqualTo(5);
        assertThat(trabajo.getImportable()).isFalse();
        assertThat(trabajo.getHashArchivoOriginal()).isNotBlank();

        List<ErrorImportacionTrabajoDto> errores = importacionTrabajoService.listarErrores(trabajo.getId());
        List<ErrorImportacionTrabajoDto> bloqueantes = errores.stream()
                .filter(e -> "ERROR".equals(e.getSeveridad()))
                .toList();
        // fila 3 (FECHA CIRUGIA inválida), fila 4 (HC vacío, obligatorio), fila 5 (EDAD inválida).
        assertThat(bloqueantes).hasSize(3);
        assertThat(bloqueantes)
                .anySatisfy(e -> {
                    assertThat(e.getNumeroFila()).isEqualTo(3);
                    assertThat(e.getNombreColumna()).isEqualTo("FECHA CIRUGIA");
                    assertThat(e.getTipoError()).isEqualTo("FORMATO_FECHA_INVALIDO");
                })
                .anySatisfy(e -> {
                    assertThat(e.getNumeroFila()).isEqualTo(4);
                    assertThat(e.getNombreColumna()).isEqualTo("HC");
                    assertThat(e.getTipoError()).isEqualTo("VALOR_OBLIGATORIO_VACIO");
                })
                .anySatisfy(e -> {
                    assertThat(e.getNumeroFila()).isEqualTo(5);
                    assertThat(e.getNombreColumna()).isEqualTo("EDAD");
                    assertThat(e.getTipoError()).isEqualTo("FORMATO_NUMERO_INVALIDO");
                });
        assertThat(trabajo.getTotalErrores()).isEqualTo(bloqueantes.size());

        // MICROOR. vacío en la fila 6 es opcional: advertencia, no error.
        assertThat(errores)
                .filteredOn(e -> "ADVERTENCIA".equals(e.getSeveridad()))
                .anySatisfy(e -> assertThat(e.getNombreColumna()).isEqualTo("MICROOR."));

        // El contenido original no se toca nunca tras crear la copia.
        ImportacionTrabajo entidad = importacionTrabajoRepository.findById(trabajo.getId()).orElseThrow();
        assertThat(entidad.getContenidoArchivo()).isEqualTo(contenidoCsv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        List<EventoImportacionTrabajo> eventos =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajo.getId());
        assertThat(eventos).extracting(e -> e.getTipoEvento().name()).contains("COPIA_CREADA");
    }

    // ---- Test 2: corregir celda y revalidar ----

    @Test
    void corregirCelda_hastaDejarLaCopiaImportable() {
        Long datasetId = crearDatasetBorrador("IMPORTACION");
        Long plantillaId = crearPlantillaConCamposHabituales(datasetId);

        String contenidoCsv = """
                HC;FECHA CIRUGIA;EDAD;MICROOR.
                H001;01/01/2026;45;Staphylococcus
                H002;fecha_mala;40;Ecoli
                ;03/01/2026;30;Proteus
                H004;04/01/2026;abc;Klebsiella
                H005;05/01/2026;28;
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        importacionTrabajoService.corregirCelda(trabajoId, 3, "FECHA CIRUGIA", "2026-01-15");
        importacionTrabajoService.corregirCelda(trabajoId, 4, "HC", "H003");
        RevalidarImportacionTrabajoResponseDto ultima =
                importacionTrabajoService.corregirCelda(trabajoId, 5, "EDAD", "77");

        ImportacionTrabajoResponseDto trabajo = ultima.getImportacionTrabajo();
        assertThat(trabajo.getEstado()).isEqualTo("LISTA_PARA_IMPORTAR");
        assertThat(trabajo.getImportable()).isTrue();
        assertThat(trabajo.getTotalErrores()).isZero();

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajoId);
        FilaImportacionTrabajo fila3 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 3).findFirst().orElseThrow();
        FilaImportacionTrabajo fila4 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 4).findFirst().orElseThrow();
        FilaImportacionTrabajo fila5 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 5).findFirst().orElseThrow();

        // El valor original nunca se sobrescribe; la corrección vive aparte, como overlay.
        assertThat(fila3.getValoresOriginales()).containsEntry("FECHA CIRUGIA", "fecha_mala");
        assertThat(fila3.getValoresCorregidos()).containsEntry("FECHA CIRUGIA", "2026-01-15");
        assertThat(fila4.getValoresOriginales()).containsEntry("HC", "");
        assertThat(fila4.getValoresCorregidos()).containsEntry("HC", "H003");
        assertThat(fila5.getValoresOriginales()).containsEntry("EDAD", "abc");
        assertThat(fila5.getValoresCorregidos()).containsEntry("EDAD", "77");
        // Ninguna otra columna de esas filas debería tener overlay.
        assertThat(fila3.getValoresCorregidos()).hasSize(1);

        List<EventoImportacionTrabajo> eventos =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        long celdasCorregidas = eventos.stream()
                .filter(e -> e.getTipoEvento().name().equals("CELDA_CORREGIDA"))
                .count();
        assertThat(celdasCorregidas).isEqualTo(3);
    }

    // ---- Test 3: tipado inválido bloquea la importación ----

    @Test
    void corregirCelda_conTipadoInvalido_bloqueaImportacionPeroVacioOpcionalSoloAvisa() {
        Long datasetId = crearDatasetBorrador("TIPADO");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long campoEdad = crearCampo(datasetId, "edad", "EDAD", TipoDatoExcel.ENTERO, false);
        Long campoExitus = crearCampo(datasetId, "exitus", "EXITUS", TipoDatoExcel.BOOLEANO, false);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla tipado");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);
        crearMapeo(plantillaId, "EDAD", campoEdad, TipoDatoExcel.ENTERO, false);
        crearMapeo(plantillaId, "EXITUS", campoExitus, TipoDatoExcel.BOOLEANO, false);

        // Fila 1: EDAD/EXITUS con valor pero inválido -> debe bloquear (ERROR).
        // Fila 2: EDAD/EXITUS vacíos (opcionales) -> solo advertencia, no bloquea.
        String contenidoCsv = """
                HC;FECHA CIRUGIA;EDAD;EXITUS
                H100;2026-01-01;abc;quizás
                H101;2026-01-02;;
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        ImportacionTrabajoResponseDto trabajo = importacionTrabajoService.obtenerPorId(trabajoId);
        assertThat(trabajo.getImportable()).isFalse();

        List<ErrorImportacionTrabajoDto> errores = importacionTrabajoService.listarErrores(trabajoId);

        ErrorImportacionTrabajoDto errorEdad = errores.stream()
                .filter(e -> e.getNumeroFila() == 2 && "EDAD".equals(e.getNombreColumna()))
                .findFirst().orElseThrow();
        assertThat(errorEdad.getTipoError()).isEqualTo("FORMATO_NUMERO_INVALIDO");
        assertThat(errorEdad.getSeveridad()).isEqualTo("ERROR");

        ErrorImportacionTrabajoDto errorExitus = errores.stream()
                .filter(e -> e.getNumeroFila() == 2 && "EXITUS".equals(e.getNombreColumna()))
                .findFirst().orElseThrow();
        assertThat(errorExitus.getTipoError()).isEqualTo("FORMATO_BOOLEANO_INVALIDO");
        assertThat(errorExitus.getSeveridad()).isEqualTo("ERROR");

        // Fila 3 (índice de archivo 3): EDAD/EXITUS vacíos, opcionales -> advertencia, no error.
        assertThat(errores)
                .filteredOn(e -> e.getNumeroFila() == 3)
                .allSatisfy(e -> assertThat(e.getSeveridad()).isEqualTo("ADVERTENCIA"));

        assertThat(trabajo.getTotalErrores()).isEqualTo(2);
    }

    // ---- Test 4: excluir fila y deshacer todas las exclusiones ----

    @Test
    void excluirFila_yLuegoDeshacerTodasLasExclusiones() {
        Long datasetId = crearDatasetBorrador("EXCLUSION");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla exclusion");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);

        String contenidoCsv = """
                HC;FECHA CIRUGIA
                H001;2026-01-01
                ;2026-01-02
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        assertThat(importacionTrabajoService.obtenerPorId(trabajoId).getImportable()).isFalse();

        RevalidarImportacionTrabajoResponseDto trasExcluir =
                importacionTrabajoService.actualizarExclusion(trabajoId, 3, true);
        assertThat(trasExcluir.getImportacionTrabajo().getTotalFilasExcluidas()).isEqualTo(1);
        assertThat(trasExcluir.getImportacionTrabajo().getImportable()).isTrue();

        FilaImportacionTrabajo filaExcluida = filaImportacionTrabajoRepository
                .findByImportacionTrabajoIdAndNumeroFilaOriginal(trabajoId, 3).orElseThrow();
        assertThat(filaExcluida.getExcluida()).isTrue();

        List<EventoImportacionTrabajo> eventosTrasExcluir =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        assertThat(eventosTrasExcluir).extracting(e -> e.getTipoEvento().name()).contains("FILA_EXCLUIDA");

        RevalidarImportacionTrabajoResponseDto trasDeshacer =
                importacionTrabajoService.deshacerTodasLasExclusiones(trabajoId);
        assertThat(trasDeshacer.getImportacionTrabajo().getTotalFilasExcluidas()).isZero();
        assertThat(trasDeshacer.getImportacionTrabajo().getImportable()).isFalse();

        FilaImportacionTrabajo filaReincluida = filaImportacionTrabajoRepository
                .findByImportacionTrabajoIdAndNumeroFilaOriginal(trabajoId, 3).orElseThrow();
        assertThat(filaReincluida.getExcluida()).isFalse();

        List<EventoImportacionTrabajo> eventosFinal =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        assertThat(eventosFinal).extracting(e -> e.getTipoEvento().name()).contains("TODAS_EXCLUSIONES_DESHECHAS");
    }

    // ---- Test 5: rellenar columna en bloque ----

    @Test
    void rellenarColumna_aplicaSoloAFilasConElProblemaYNoTocaElOriginal() {
        Long datasetId = crearDatasetBorrador("RELLENAR");
        Long plantillaId = crearPlantillaConCamposHabituales(datasetId);

        String contenidoCsv = """
                HC;FECHA CIRUGIA;EDAD;MICROOR.
                H001;2026-01-01;40;Ecoli
                H002;2026-01-02;41;
                H003;2026-01-03;42;
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        RevalidarImportacionTrabajoResponseDto resultado = importacionTrabajoService.rellenarColumna(
                trabajoId, "MICROOR.", "VALOR_OBLIGATORIO_VACIO", "No informado", true);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajoId);
        FilaImportacionTrabajo fila2 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 2).findFirst().orElseThrow();
        FilaImportacionTrabajo fila3 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 3).findFirst().orElseThrow();

        assertThat(fila2.getValoresCorregidos()).doesNotContainKey("MICROOR.");
        assertThat(fila3.getValoresCorregidos()).containsEntry("MICROOR.", "No informado");
        // El original vacío se conserva intacto: solo se añade el overlay.
        assertThat(fila3.getValoresOriginales()).containsEntry("MICROOR.", "");

        // La copia ya no tiene advertencias pendientes en MICROOR. tras rellenar.
        assertThat(resultado.getImportacionTrabajo().getTotalAdvertencias()).isZero();

        List<EventoImportacionTrabajo> eventos =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        assertThat(eventos).extracting(e -> e.getTipoEvento().name()).contains("COLUMNA_RELLENADA");
    }

    // ---- Test 6: normalizar columna de fecha ----

    @Test
    void normalizarColumna_unificaFormatosDeFechaYRespetaFechasImposibles() {
        Long datasetId = crearDatasetBorrador("NORMALIZAR");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla normalizar");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);

        String contenidoCsv = """
                HC;FECHA CIRUGIA
                H001;01/02/2026
                H002;2026-02-03
                H003;04-02-2026
                H004;no es una fecha
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        assertThat(importacionTrabajoService.obtenerPorId(trabajoId).getTotalErrores()).isEqualTo(1);

        RevalidarImportacionTrabajoResponseDto resultado =
                importacionTrabajoService.normalizarColumna(trabajoId, "FECHA CIRUGIA", "FECHA");

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajoId);
        FilaImportacionTrabajo fila1 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 2).findFirst().orElseThrow();
        FilaImportacionTrabajo fila2 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 3).findFirst().orElseThrow();
        FilaImportacionTrabajo fila3 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 4).findFirst().orElseThrow();
        FilaImportacionTrabajo fila4 = filas.stream().filter(f -> f.getNumeroFilaOriginal() == 5).findFirst().orElseThrow();

        assertThat(fila1.getValoresCorregidos()).containsEntry("FECHA CIRUGIA", "2026-02-01");
        // Ya estaba en ISO: no hace falta corrección, no se toca.
        assertThat(fila2.getValoresCorregidos()).doesNotContainKey("FECHA CIRUGIA");
        assertThat(fila3.getValoresCorregidos()).containsEntry("FECHA CIRUGIA", "2026-02-04");
        // Fecha imposible: no se puede normalizar, el error bloqueante sigue.
        assertThat(fila4.getValoresCorregidos()).doesNotContainKey("FECHA CIRUGIA");

        assertThat(resultado.getImportacionTrabajo().getTotalErrores()).isEqualTo(1);
        assertThat(resultado.getImportacionTrabajo().getImportable()).isFalse();

        List<EventoImportacionTrabajo> eventos =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        assertThat(eventos).extracting(e -> e.getTipoEvento().name()).contains("COLUMNA_NORMALIZADA");
    }

    // ---- Test 7: restaurar copia al estado original ----

    @Test
    void restaurarOriginal_deshaceCorreccionesRellenosYExclusiones() {
        Long datasetId = crearDatasetBorrador("RESTAURAR");
        Long plantillaId = crearPlantillaConCamposHabituales(datasetId);

        String contenidoCsv = """
                HC;FECHA CIRUGIA;EDAD;MICROOR.
                H001;fecha_mala;40;
                H002;2026-01-02;41;
                """;
        MockMultipartFile archivo = archivoCsv(contenidoCsv);
        Long trabajoId = importacionTrabajoService.crear(archivo, plantillaId, 0, 0)
                .getImportacionTrabajo().getId();
        String hashOriginal = importacionTrabajoService.obtenerPorId(trabajoId).getHashArchivoOriginal();

        importacionTrabajoService.corregirCelda(trabajoId, 2, "FECHA CIRUGIA", "2026-01-01");
        importacionTrabajoService.rellenarColumna(trabajoId, "MICROOR.", "VALOR_OBLIGATORIO_VACIO", "No informado", true);
        importacionTrabajoService.actualizarExclusion(trabajoId, 3, true);

        RevalidarImportacionTrabajoResponseDto restaurado = importacionTrabajoService.restaurarOriginal(trabajoId);

        assertThat(restaurado.getImportacionTrabajo().getHashArchivoOriginal()).isEqualTo(hashOriginal);
        // El error original (fecha_mala en la fila 2) vuelve a estar presente.
        assertThat(restaurado.getImportacionTrabajo().getTotalErrores()).isGreaterThanOrEqualTo(1);

        List<FilaImportacionTrabajo> filas =
                filaImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByNumeroFilaOriginalAsc(trabajoId);
        assertThat(filas).allSatisfy(f -> {
            assertThat(f.getExcluida()).isFalse();
            assertThat(f.getValoresCorregidos()).isNullOrEmpty();
        });

        List<ErrorImportacionTrabajoDto> errores = importacionTrabajoService.listarErrores(trabajoId);
        assertThat(errores)
                .anySatisfy(e -> {
                    assertThat(e.getNumeroFila()).isEqualTo(2);
                    assertThat(e.getNombreColumna()).isEqualTo("FECHA CIRUGIA");
                    assertThat(e.getTipoError()).isEqualTo("FORMATO_FECHA_INVALIDO");
                });

        List<EventoImportacionTrabajo> eventos =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        assertThat(eventos).extracting(e -> e.getTipoEvento().name()).contains("COPIA_RESTAURADA_ORIGINAL");
    }

    // ---- Test 8: importar desde copia y activar el dataset ----

    @Test
    void importarDesdeTrabajo_creaRegistrosYActivarDatasetFunciona() {
        Long datasetId = crearDatasetBorrador("IMPORTAR");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla importar");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);

        String contenidoCsv = """
                HC;FECHA CIRUGIA
                H001;2026-01-01
                H002;2026-01-02
                ;2026-01-03
                """;
        Long trabajoId = importacionTrabajoService.crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo().getId();

        // La fila 4 (HC vacío) se excluye para poder importar; no debe aparecer en el resultado.
        importacionTrabajoService.actualizarExclusion(trabajoId, 4, true);
        assertThat(importacionTrabajoService.obtenerPorId(trabajoId).getImportable()).isTrue();

        ImportarDesdeTrabajoResponseDto resultado = importacionTrabajoService.importarDesdeTrabajo(trabajoId);

        assertThat(resultado.getImportacionTrabajo().getEstado()).isEqualTo("IMPORTADA");
        assertThat(resultado.getFilasImportadas()).isEqualTo(2);
        assertThat(resultado.getFilasExcluidas()).isEqualTo(1);
        Long importacionGenericaId = resultado.getImportacionGenerica().getImportacionId();
        assertThat(importacionGenericaId).isNotNull();

        assertThat(registroClinicoGenericoRepository.findByDatasetId(datasetId)).hasSize(2);

        ImportacionTrabajo entidad = importacionTrabajoRepository.findById(trabajoId).orElseThrow();
        assertThat(entidad.getImportacionGenerica()).isNotNull();
        assertThat(entidad.getImportacionGenerica().getId()).isEqualTo(importacionGenericaId);

        List<EventoImportacionTrabajo> eventosTrasImportar =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        assertThat(eventosTrasImportar).extracting(e -> e.getTipoEvento().name()).contains("IMPORTACION_REALIZADA");

        var datasetActivado = datasetClinicoService.activar(datasetId);
        assertThat(datasetActivado.getEstadoDataset()).isEqualTo("ACTIVO");

        List<EventoImportacionTrabajo> eventosTrasActivar =
                eventoImportacionTrabajoRepository.findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId);
        assertThat(eventosTrasActivar).extracting(e -> e.getTipoEvento().name()).contains("DATASET_ACTIVADO");
    }

    // ---- Test 13: SIN_FILAS_CLINICAS (protege el bug de "Columna desconocida" falsa) ----

    @Test
    void archivoSinFilasClinicasReconocibles_generaErrorGlobalNoDeColumna() {
        Long datasetId = crearDatasetBorrador("SINFILAS");
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla sin filas");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);

        // Ninguna columna mapeada tiene valor en ninguna fila: no hay fila clínica reconocible.
        String contenidoCsv = """
                HC;FECHA CIRUGIA
                ;
                """;
        ImportacionTrabajoResponseDto trabajo = importacionTrabajoService
                .crear(archivoCsv(contenidoCsv), plantillaId, 0, 0)
                .getImportacionTrabajo();

        assertThat(trabajo.getTotalFilasLeidas()).isZero();
        assertThat(trabajo.getImportable()).isFalse();

        List<ErrorImportacionTrabajoDto> errores = importacionTrabajoService.listarErrores(trabajo.getId());
        assertThat(errores).hasSize(1);
        ErrorImportacionTrabajoDto error = errores.get(0);
        assertThat(error.getTipoError()).isEqualTo("SIN_FILAS_CLINICAS");
        assertThat(error.getNombreColumna()).isNull();
        assertThat(error.getSeveridad()).isEqualTo("ERROR");
    }

    // ---- Helper específico de esta clase ----

    private Long crearPlantillaConCamposHabituales(Long datasetId) {
        Long campoHc = crearCampo(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        Long campoFecha = crearCampo(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA, true);
        Long campoEdad = crearCampo(datasetId, "edad", "EDAD", TipoDatoExcel.ENTERO, false);
        Long campoMicroor = crearCampo(datasetId, "microor", "MICROOR.", TipoDatoExcel.TEXTO, false);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla habitual");
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);
        crearMapeo(plantillaId, "EDAD", campoEdad, TipoDatoExcel.ENTERO, false);
        crearMapeo(plantillaId, "MICROOR.", campoMicroor, TipoDatoExcel.TEXTO, false);
        return plantillaId;
    }

    // ---- Red de seguridad: nada de esta fase debe quedar residual en la BD ----

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void noQuedanDatasetsResidualesDeLaFase68F() {
        boolean hayResiduos = datasetClinicoRepository.findAll().stream()
                .anyMatch(d -> d.getCodigo() != null && d.getCodigo().startsWith(PREFIJO_CODIGO));
        assertThat(hayResiduos)
                .as("No debe quedar ningún dataset TEST_68F_* tras el rollback de los tests anteriores")
                .isFalse();
    }
}
