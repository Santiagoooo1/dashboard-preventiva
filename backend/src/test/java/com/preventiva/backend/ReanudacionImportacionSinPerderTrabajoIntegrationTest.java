package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.ImportacionTrabajoResponseDto;
import com.preventiva.backend.dto.ImportarDesdeTrabajoResponseDto;
import com.preventiva.backend.dto.ReanudarBorradorDatasetDto;
import com.preventiva.backend.entity.FilaImportacionTrabajo;
import com.preventiva.backend.entity.ImportacionTrabajo;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.EstadoDatasetClinico;
import com.preventiva.backend.enums.EstadoImportacionTrabajo;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoEventoImportacionTrabajo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 6.9K — reanudar una importación guiada sin perder las correcciones ni
 * duplicar el borrador.
 *
 * <p>El defecto que cubren estos tests: salir de la pantalla de corrección
 * descartaba la copia de trabajo por su cuenta, así que al volver por
 * "Continuar creación" el borrador aparecía sin nada que reanudar y la
 * aplicación pedía el Excel otra vez, pese a tener el archivo y las filas
 * guardados en la base de datos. En paralelo, cada reintento creaba un dataset
 * nuevo con sufijo (CODIGO_2, CODIGO_3…).
 *
 * <p>Se prueba contra los servicios reales, igual que el resto de tests de esta
 * familia, y todo se revierte al terminar cada método.
 */
class ReanudacionImportacionSinPerderTrabajoIntegrationTest extends AbstractImportacionTrabajoFase68FTest {

    /** Dos filas válidas y una con la fecha rota: da trabajo corregible, no un archivo inservible. */
    private static final String CSV_CON_UN_ERROR =
            "HC;FECHA CIRUGIA;SERVICIO\n"
                    + "H001;2026-01-10;Trauma\n"
                    + "H002;fecha_mala;Trauma\n"
                    + "H003;2026-01-12;Cesareas\n";

    /**
     * Número de la fila que trae la fecha rota. La cabecera es la fila 1, así
     * que H002 —el registro con "fecha_mala"— es la 3.
     */
    private static final int FILA_CON_FECHA_ROTA = 3;

    /** Contexto mínimo de una importación: dataset borrador, campos, plantilla y mapeos. */
    private record Escenario(Long datasetId, Long plantillaId, Long campoHc, Long campoFecha, Long campoServicio) {
    }

    private Escenario prepararEscenario(String sufijo) {
        Long datasetId = crearDatasetBorrador(sufijo);
        // Campos comunes: paciente, fecha y servicio van a columnas físicas del
        // registro, que es donde se comprueba luego el valor importado.
        Long campoHc = crearCampoComun(datasetId, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO);
        Long campoFecha = crearCampoComun(datasetId, "fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA);
        Long campoServicio = crearCampoComun(datasetId, "servicio", "SERVICIO", TipoDatoExcel.TEXTO);
        Long plantillaId = crearPlantilla(datasetId, "Plantilla " + sufijo);
        crearMapeo(plantillaId, "HC", campoHc, TipoDatoExcel.TEXTO, true);
        crearMapeo(plantillaId, "FECHA CIRUGIA", campoFecha, TipoDatoExcel.FECHA, true);
        crearMapeo(plantillaId, "SERVICIO", campoServicio, TipoDatoExcel.TEXTO, false);
        return new Escenario(datasetId, plantillaId, campoHc, campoFecha, campoServicio);
    }

    private Long crearCampoComun(Long datasetId, String codigo, String etiqueta, TipoDatoExcel tipoDato) {
        return campoClinicoService.crear(datasetId, campoRequest(codigo, etiqueta, tipoDato)).getId();
    }

    private Long crearTrabajo(Escenario escenario, String csv) {
        return importacionTrabajoService.crear(archivoCsv(csv), escenario.plantillaId(), 0, 0)
                .getImportacionTrabajo().getId();
    }

    /** Las columnas que el asistente vuelve a declarar en cada intento. */
    private List<CampoClinicoRequestDto> columnasDelAsistente() {
        return List.of(
                campoRequest("pacienteCodigo", "HC", TipoDatoExcel.TEXTO),
                campoRequest("fechaEvento", "FECHA CIRUGIA", TipoDatoExcel.FECHA),
                campoRequest("servicio", "SERVICIO", TipoDatoExcel.TEXTO));
    }

    private CampoClinicoRequestDto campoRequest(String codigo, String etiqueta, TipoDatoExcel tipoDato) {
        CampoClinicoRequestDto request = new CampoClinicoRequestDto();
        request.setCodigo(codigo);
        request.setEtiqueta(etiqueta);
        request.setTipoDato(tipoDato);
        request.setEsComun(true);
        request.setObligatorio(false);
        return request;
    }

    private ImportacionTrabajo recargar(Long trabajoId) {
        return importacionTrabajoRepository.findById(trabajoId).orElseThrow();
    }

    /**
     * Deja la copia como la dejaba el defecto anterior a la Fase 6.9K:
     * descartada sin que nadie lo pidiera y, por tanto, sin marca de intención.
     * Indistinguible de un descarte legítimo hecho antes de que existiera esa
     * marca, que es justo lo que obliga a preguntar al usuario.
     */
    private void descartarComoElDefectoAntiguo(Long trabajoId) {
        ImportacionTrabajo trabajo = recargar(trabajoId);
        trabajo.setEstado(EstadoImportacionTrabajo.DESCARTADA);
        trabajo.setDescarteExplicito(null);
        importacionTrabajoRepository.saveAndFlush(trabajo);
    }

    private long eventosDeTipo(Long trabajoId, TipoEventoImportacionTrabajo tipo) {
        return eventoImportacionTrabajoRepository
                .findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId).stream()
                .filter(e -> e.getTipoEvento() == tipo)
                .count();
    }

    private FilaImportacionTrabajo fila(Long trabajoId, int numeroFila) {
        return filaImportacionTrabajoRepository
                .findByImportacionTrabajoIdAndNumeroFilaOriginal(trabajoId, numeroFila).orElseThrow();
    }

    // ------------------------------------------------------------------
    // A–C: qué deja el análisis inicial, y qué NO
    // ------------------------------------------------------------------

    /** A: analizar un archivo deja exactamente un borrador, una copia y una fila por registro. */
    @Test
    void analizarArchivo_dejaUnBorradorUnaCopiaDeTrabajoYUnaFilaPorRegistro() {
        Escenario escenario = prepararEscenario("ANALISIS");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);

        assertThat(datasetClinicoRepository.findById(escenario.datasetId()).orElseThrow().getEstadoDataset())
                .isEqualTo(EstadoDatasetClinico.BORRADOR);
        assertThat(importacionTrabajoRepository.findByDatasetId(escenario.datasetId())).hasSize(1);
        assertThat(filaImportacionTrabajoRepository.countByImportacionTrabajoId(trabajoId)).isEqualTo(3);
        assertThat(recargar(trabajoId).getTotalFilasLeidas()).isEqualTo(3);
    }

    /** B: una fila con la fecha rota deja la copia EN_EDICION, no en un estado terminal. */
    @Test
    void erroresCorregibles_dejanLaCopiaEnEdicion() {
        Escenario escenario = prepararEscenario("EN_EDICION");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);

        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.EN_EDICION);
        assertThat(importacionTrabajoService.obtenerPorId(trabajoId).getImportable()).isFalse();
    }

    /**
     * C: el defecto original en una sola frase — nada de lo que hace el usuario
     * sin pedir un descarte debe dejar la copia en DESCARTADA.
     */
    @Test
    void trabajarYReanudarVariasVeces_noDescartaLaCopiaPorSuCuenta() {
        Escenario escenario = prepararEscenario("NO_DESCARTA");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);

        importacionTrabajoService.revalidar(trabajoId);
        importacionTrabajoService.listarErrores(trabajoId);
        importacionTrabajoService.listarFilas(trabajoId, 0, 50, false);
        datasetClinicoService.reanudarBorrador(escenario.datasetId());
        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");
        datasetClinicoService.reanudarBorrador(escenario.datasetId());

        assertThat(recargar(trabajoId).getEstado()).isNotEqualTo(EstadoImportacionTrabajo.DESCARTADA);
        assertThat(recargar(trabajoId).getDescarteExplicito()).isNull();
    }

    // ------------------------------------------------------------------
    // D–E: las correcciones se guardan y sobreviven
    // ------------------------------------------------------------------

    /** D: corregir una celda escribe en valoresCorregidos sin tocar valoresOriginales. */
    @Test
    void corregirCelda_persisteEnValoresCorregidosYConservaElOriginal() {
        Escenario escenario = prepararEscenario("CORRECCION");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);

        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");

        FilaImportacionTrabajo fila = fila(trabajoId, FILA_CON_FECHA_ROTA);
        assertThat(fila.getValoresCorregidos()).containsEntry("FECHA CIRUGIA", "2026-01-11");
        assertThat(fila.getValoresOriginales()).containsEntry("FECHA CIRUGIA", "fecha_mala");
    }

    /** E: salir del asistente y volver por "Continuar creación" no pierde la corrección. */
    @Test
    void salirYVolverPorContinuarCreacion_conservaLasCorrecciones() {
        Escenario escenario = prepararEscenario("SOBREVIVE");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");

        // "Salir" no es una llamada al backend: es no hacer nada. Lo que se
        // comprueba es que volver a entrar encuentra la corrección donde estaba.
        ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());

        assertThat(reanudacion.getImportacionTrabajoId()).isEqualTo(trabajoId);
        assertThat(reanudacion.getTotalCorrecciones()).isEqualTo(1);
        assertThat(fila(trabajoId, FILA_CON_FECHA_ROTA).getValoresCorregidos()).containsEntry("FECHA CIRUGIA", "2026-01-11");
    }

    // ------------------------------------------------------------------
    // F–K: reanudar no duplica nada
    // ------------------------------------------------------------------

    /** F y G: se reanuda la misma copia sobre el mismo dataset. */
    @Test
    void reanudar_devuelveLaMismaCopiaYElMismoDataset() {
        Escenario escenario = prepararEscenario("MISMO_TRABAJO");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);

        ReanudarBorradorDatasetDto primera = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        ReanudarBorradorDatasetDto segunda = datasetClinicoService.reanudarBorrador(escenario.datasetId());

        assertThat(primera.getImportacionTrabajoId()).isEqualTo(trabajoId);
        assertThat(segunda.getImportacionTrabajoId()).isEqualTo(trabajoId);
        assertThat(primera.getDatasetId()).isEqualTo(escenario.datasetId());
        assertThat(segunda.getDatasetId()).isEqualTo(escenario.datasetId());
        assertThat(primera.getPasoRecomendado()).isEqualTo("CORREGIR_FILAS");
        assertThat(primera.getNombreArchivoOriginal()).isNotBlank();
    }

    /**
     * H–K: reanudar vuelve a declarar las mismas columnas. Antes eso reventaba
     * en la primera ("Ya existe un campo clínico con el código 'pacienteCodigo'");
     * ahora los campos se reutilizan con su id intacto y sin filas repetidas.
     */
    @Test
    void reanudar_reutilizaLosCamposExistentesSinDuplicarlos() {
        Escenario escenario = prepararEscenario("CAMPOS");
        crearTrabajo(escenario, CSV_CON_UN_ERROR);

        List<CampoClinicoResponseDto> campos =
                campoClinicoService.asegurarParaImportacion(escenario.datasetId(), columnasDelAsistente());

        assertThat(campos).extracting(CampoClinicoResponseDto::getId)
                .containsExactly(escenario.campoHc(), escenario.campoFecha(), escenario.campoServicio());

        for (String codigo : List.of("pacienteCodigo", "fechaEvento", "servicio")) {
            assertThat(campoClinicoRepository.findByDatasetId(escenario.datasetId()).stream()
                    .filter(c -> c.getCodigo().equalsIgnoreCase(codigo))
                    .count())
                    .as("filas con el código %s", codigo)
                    .isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------
    // L–N: importar desde lo persistido, sin volver a pedir el archivo
    // ------------------------------------------------------------------

    /**
     * L y M: una vez corregida, la copia se importa sin que el navegador
     * aporte ningún archivo, y lo que llega a la base es el valor corregido.
     */
    @Test
    void importarDesdeTrabajo_noNecesitaArchivoYUsaElValorCorregido() {
        Escenario escenario = prepararEscenario("IMPORTAR");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");

        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR);

        // Ni un MultipartFile a la vista: todo sale de lo que ya está guardado.
        ImportarDesdeTrabajoResponseDto resultado = importacionTrabajoService.importarDesdeTrabajo(trabajoId);

        assertThat(resultado.getImportacionGenerica().getFilasImportadas()).isEqualTo(3);
        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.IMPORTADA);

        List<RegistroClinicoGenerico> registros =
                registroClinicoGenericoRepository.findByDatasetId(escenario.datasetId());
        assertThat(registros).hasSize(3);
        assertThat(registros).anySatisfy(registro -> {
            assertThat(registro.getPacienteCodigo()).isEqualTo("H002");
            // La fecha rota del archivo llegó corregida, no como estaba en el Excel.
            assertThat(registro.getFechaEvento()).hasToString("2026-01-11");
        });
    }

    /** N: el archivo original sigue disponible en cada etapa; nunca se vacía. */
    @Test
    void contenidoArchivo_siguePresenteDuranteTodoElTrabajo() {
        Escenario escenario = prepararEscenario("ARCHIVO");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        byte[] alCrear = recargar(trabajoId).getContenidoArchivo();
        assertThat(alCrear).isNotEmpty();

        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");
        assertThat(recargar(trabajoId).getContenidoArchivo()).isEqualTo(alCrear);

        datasetClinicoService.reanudarBorrador(escenario.datasetId());
        assertThat(recargar(trabajoId).getContenidoArchivo()).isEqualTo(alCrear);

        importacionTrabajoService.importarDesdeTrabajo(trabajoId);
        assertThat(recargar(trabajoId).getContenidoArchivo()).isEqualTo(alCrear);
    }

    // ------------------------------------------------------------------
    // O–Q: un proceso, un dataset
    // ------------------------------------------------------------------

    /** O: tres reanudaciones seguidas no fabrican borradores nuevos. */
    @Test
    void tresReanudaciones_dejanUnSoloDataset() {
        Escenario escenario = prepararEscenario("TRES_REANUDACIONES");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        String codigo = datasetClinicoRepository.findById(escenario.datasetId()).orElseThrow().getCodigo();

        for (int i = 0; i < 3; i++) {
            ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());
            assertThat(reanudacion.getDatasetId()).isEqualTo(escenario.datasetId());
            assertThat(reanudacion.getImportacionTrabajoId()).isEqualTo(trabajoId);
        }

        assertThat(datasetsConPrefijo(codigo)).isEqualTo(1);
    }

    /**
     * P y Q: tres reintentos de la misma importación (rehacer campos y volver a
     * validar) siguen dejando un único dataset, sin CODIGO_2 ni CODIGO_3.
     */
    @Test
    void tresReintentos_dejanUnSoloDatasetYNingunSufijo() {
        Escenario escenario = prepararEscenario("TRES_REINTENTOS");
        String codigo = datasetClinicoRepository.findById(escenario.datasetId()).orElseThrow().getCodigo();

        for (int i = 0; i < 3; i++) {
            campoClinicoService.asegurarParaImportacion(escenario.datasetId(), columnasDelAsistente());
            crearTrabajo(escenario, CSV_CON_UN_ERROR);
        }

        assertThat(datasetsConPrefijo(codigo)).isEqualTo(1);
        assertThat(datasetClinicoRepository.findAll().stream()
                .anyMatch(d -> d.getCodigo().equals(codigo + "_2") || d.getCodigo().equals(codigo + "_3")))
                .as("no debe haber datasets sufijados por reintentar")
                .isFalse();
    }

    private long datasetsConPrefijo(String codigo) {
        return datasetClinicoRepository.findAll().stream()
                .filter(d -> d.getCodigo().startsWith(codigo))
                .count();
    }

    // ------------------------------------------------------------------
    // R–S: sustituir archivo
    // ------------------------------------------------------------------

    /**
     * R y S: sustituir el archivo es una decisión del usuario. El intento
     * anterior se descarta de forma explícita, nace otro, y el dataset sigue
     * siendo el mismo.
     */
    @Test
    void sustituirArchivo_conservaElDatasetYDescartaElIntentoAnteriorDeFormaExplicita() {
        Escenario escenario = prepararEscenario("SUSTITUIR");
        Long primerTrabajo = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        String codigo = datasetClinicoRepository.findById(escenario.datasetId()).orElseThrow().getCodigo();

        importacionTrabajoService.descartar(primerTrabajo);
        Long segundoTrabajo = crearTrabajo(escenario,
                "HC;FECHA CIRUGIA;SERVICIO\nH010;2026-02-01;Trauma\n");

        assertThat(segundoTrabajo).isNotEqualTo(primerTrabajo);
        assertThat(recargar(primerTrabajo).getEstado()).isEqualTo(EstadoImportacionTrabajo.DESCARTADA);
        assertThat(recargar(primerTrabajo).getDescarteExplicito()).isTrue();
        assertThat(datasetsConPrefijo(codigo)).isEqualTo(1);

        ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        assertThat(reanudacion.getDatasetId()).isEqualTo(escenario.datasetId());
        assertThat(reanudacion.getImportacionTrabajoId()).isEqualTo(segundoTrabajo);
    }

    // ------------------------------------------------------------------
    // C–H: la revisión anterior se ofrece, nunca se reactiva sola
    // ------------------------------------------------------------------

    /** C: lo que el usuario tiró a conciencia ni siquiera se ofrece. */
    @Test
    void copiaDescartadaAProposito_niSeOfreceNiSeRecupera() {
        Escenario escenario = prepararEscenario("DESCARTE_LEGITIMO");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);

        importacionTrabajoService.descartar(trabajoId);

        assertThat(importacionTrabajoService.buscarTrabajoHistoricoRecuperable(escenario.datasetId())).isEmpty();

        ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        assertThat(reanudacion.getImportacionTrabajoId()).isNull();
        assertThat(reanudacion.isHayTrabajoHistoricoRecuperable()).isFalse();
        assertThat(reanudacion.isHuboCopiaDescartada()).isTrue();
        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.DESCARTADA);

        // Ni siquiera pidiéndolo a la cara: un descarte explícito es definitivo.
        assertThatThrownBy(() -> importacionTrabajoService.recuperarTrabajoAnterior(escenario.datasetId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.DESCARTADA);
    }

    /**
     * D: el punto de esta fase. Una copia descartada sin marca de intención es
     * ambigua —pudo tirarla el defecto del asistente o el propio usuario antes
     * de que existiera la marca—, así que reanudar la ANUNCIA y no la toca.
     */
    @Test
    void copiaDescartadaAmbigua_seAnunciaPeroNoCambiaDeEstado() {
        Escenario escenario = prepararEscenario("HISTORICO_AMBIGUO");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");
        descartarComoElDefectoAntiguo(trabajoId);

        ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());

        assertThat(reanudacion.isHayTrabajoHistoricoRecuperable()).isTrue();
        assertThat(reanudacion.getTrabajoHistoricoId()).isEqualTo(trabajoId);
        assertThat(reanudacion.getNombreArchivoHistorico()).isNotBlank();
        assertThat(reanudacion.getTotalFilasHistoricas()).isEqualTo(3);
        assertThat(reanudacion.getTotalCorreccionesHistoricas()).isEqualTo(1);
        // No se presenta como copia vigente: no lo es todavía.
        assertThat(reanudacion.getImportacionTrabajoId()).isNull();

        // Y sobre todo: nada ha cambiado en la base de datos.
        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.DESCARTADA);
        assertThat(eventosDeTipo(trabajoId, TipoEventoImportacionTrabajo.COPIA_RECUPERADA)).isZero();
    }

    /** E: repetir "Continuar creación" sobre esa histórica sigue sin escribir nada. */
    @Test
    void reanudarVariasVecesConHistoricoAmbiguo_noEscribeNada() {
        Escenario escenario = prepararEscenario("HISTORICO_SIN_MUTAR");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        descartarComoElDefectoAntiguo(trabajoId);
        int eventosAntes = eventoImportacionTrabajoRepository
                .findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId).size();

        ReanudarBorradorDatasetDto primera = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        ReanudarBorradorDatasetDto segunda = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        ReanudarBorradorDatasetDto tercera = datasetClinicoService.reanudarBorrador(escenario.datasetId());

        assertThat(primera.getTrabajoHistoricoId()).isEqualTo(trabajoId);
        assertThat(segunda.getTrabajoHistoricoId()).isEqualTo(trabajoId);
        assertThat(tercera.getTrabajoHistoricoId()).isEqualTo(trabajoId);
        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.DESCARTADA);
        assertThat(recargar(trabajoId).getDescarteExplicito()).isNull();
        assertThat(eventoImportacionTrabajoRepository
                .findByImportacionTrabajoIdOrderByFechaEventoAsc(trabajoId))
                .as("consultar el borrador no debe generar eventos")
                .hasSize(eventosAntes);
        assertThat(importacionTrabajoRepository.findByDatasetId(escenario.datasetId())).hasSize(1);
    }

    /**
     * F: pulsar "Recuperar trabajo anterior". Es la única acción que resucita la
     * copia: mismo dataset, misma copia, mismas filas, mismas correcciones, y
     * revalidada —ya estaba corregida, así que vuelve lista para importar—.
     */
    @Test
    void recuperarTrabajoAnterior_devuelveLaCopiaConSusCorreccionesYUnSoloEvento() {
        Escenario escenario = prepararEscenario("RECUPERACION_EXPLICITA");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");
        descartarComoElDefectoAntiguo(trabajoId);

        ImportacionTrabajoResponseDto recuperado =
                importacionTrabajoService.recuperarTrabajoAnterior(escenario.datasetId());

        assertThat(recuperado.getId()).isEqualTo(trabajoId);
        assertThat(recuperado.getDatasetId()).isEqualTo(escenario.datasetId());
        assertThat(recuperado.getEstado()).isEqualTo(EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR.name());
        assertThat(recargar(trabajoId).getEstado()).isEqualTo(EstadoImportacionTrabajo.LISTA_PARA_IMPORTAR);
        assertThat(filaImportacionTrabajoRepository.countByImportacionTrabajoId(trabajoId)).isEqualTo(3);
        assertThat(fila(trabajoId, FILA_CON_FECHA_ROTA).getValoresCorregidos())
                .containsEntry("FECHA CIRUGIA", "2026-01-11");
        assertThat(eventosDeTipo(trabajoId, TipoEventoImportacionTrabajo.COPIA_RECUPERADA)).isEqualTo(1);

        // Y a partir de aquí el borrador se reanuda por el camino normal.
        ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        assertThat(reanudacion.getImportacionTrabajoId()).isEqualTo(trabajoId);
        assertThat(reanudacion.isHayTrabajoHistoricoRecuperable()).isFalse();
        assertThat(reanudacion.getDatasetId()).isEqualTo(escenario.datasetId());
    }

    /** F (variante): sin correcciones previas vuelve a EN_EDICION, no a "lista". */
    @Test
    void recuperarTrabajoAnteriorSinCorregir_vuelveAEnEdicion() {
        Escenario escenario = prepararEscenario("RECUPERACION_CON_ERRORES");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        descartarComoElDefectoAntiguo(trabajoId);

        ImportacionTrabajoResponseDto recuperado =
                importacionTrabajoService.recuperarTrabajoAnterior(escenario.datasetId());

        assertThat(recuperado.getEstado()).isEqualTo(EstadoImportacionTrabajo.EN_EDICION.name());
        assertThat(recuperado.getImportable()).isFalse();
        assertThat(recuperado.getTotalErrores()).isPositive();
    }

    /** G: recuperar dos veces devuelve lo mismo y no duplica copias ni eventos. */
    @Test
    void recuperarDosVeces_esIdempotenteYNoDuplicaEventos() {
        Escenario escenario = prepararEscenario("RECUPERACION_IDEMPOTENTE");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");
        descartarComoElDefectoAntiguo(trabajoId);

        ImportacionTrabajoResponseDto primera =
                importacionTrabajoService.recuperarTrabajoAnterior(escenario.datasetId());
        ImportacionTrabajoResponseDto segunda =
                importacionTrabajoService.recuperarTrabajoAnterior(escenario.datasetId());

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(segunda.getEstado()).isEqualTo(primera.getEstado());
        assertThat(segunda.getTotalErrores()).isEqualTo(primera.getTotalErrores());
        assertThat(importacionTrabajoRepository.findByDatasetId(escenario.datasetId())).hasSize(1);
        assertThat(filaImportacionTrabajoRepository.countByImportacionTrabajoId(trabajoId)).isEqualTo(3);
        // El rescate ocurrió una vez, no dos.
        assertThat(eventosDeTipo(trabajoId, TipoEventoImportacionTrabajo.COPIA_RECUPERADA)).isEqualTo(1);
    }

    /** Habiendo una copia viva, la descartada ni se ofrece ni se toca. */
    @Test
    void habiendoUnaCopiaVigente_noSeOfreceLaDescartada() {
        Escenario escenario = prepararEscenario("NO_RESCATE");
        Long antigua = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        descartarComoElDefectoAntiguo(antigua);

        Long vigente = crearTrabajo(escenario, CSV_CON_UN_ERROR);

        assertThat(importacionTrabajoService.buscarTrabajoHistoricoRecuperable(escenario.datasetId())).isEmpty();
        assertThat(recargar(antigua).getEstado()).isEqualTo(EstadoImportacionTrabajo.DESCARTADA);

        ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        assertThat(reanudacion.getImportacionTrabajoId()).isEqualTo(vigente);
        assertThat(reanudacion.isHayTrabajoHistoricoRecuperable()).isFalse();
    }

    /**
     * H: sustituir el archivo descarta el intento anterior de forma explícita.
     * Eso lo saca del alcance del mecanismo histórico para siempre: no queremos
     * que la copia que el usuario acaba de sustituir reaparezca ofreciéndose.
     */
    @Test
    void sustituirArchivo_noDejaElIntentoAnteriorComoRecuperable() {
        Escenario escenario = prepararEscenario("SUSTITUIR_SIN_RESCATE");
        Long primerTrabajo = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        String codigo = datasetClinicoRepository.findById(escenario.datasetId()).orElseThrow().getCodigo();

        importacionTrabajoService.descartar(primerTrabajo);
        Long segundoTrabajo = crearTrabajo(escenario,
                "HC;FECHA CIRUGIA;SERVICIO\nH010;2026-02-01;Trauma\n");

        assertThat(datasetsConPrefijo(codigo)).isEqualTo(1);
        assertThat(importacionTrabajoService.buscarTrabajoHistoricoRecuperable(escenario.datasetId())).isEmpty();

        ReanudarBorradorDatasetDto reanudacion = datasetClinicoService.reanudarBorrador(escenario.datasetId());
        assertThat(reanudacion.getDatasetId()).isEqualTo(escenario.datasetId());
        assertThat(reanudacion.getImportacionTrabajoId()).isEqualTo(segundoTrabajo);
        assertThat(reanudacion.isHayTrabajoHistoricoRecuperable()).isFalse();

        // Y si además se descarta el segundo, el primero sigue fuera de juego.
        importacionTrabajoService.descartar(segundoTrabajo);
        assertThat(importacionTrabajoService.buscarTrabajoHistoricoRecuperable(escenario.datasetId())).isEmpty();
    }

    // ------------------------------------------------------------------
    // V: descartar el borrador sigue limpiando de verdad
    // ------------------------------------------------------------------

    /** V: la acción explícita de descartar el borrador no se ha ablandado. */
    @Test
    void descartarBorrador_sigueEliminandoDatasetCopiasYFilas() {
        Escenario escenario = prepararEscenario("DESCARTAR_BORRADOR");
        Long trabajoId = crearTrabajo(escenario, CSV_CON_UN_ERROR);
        importacionTrabajoService.corregirCelda(trabajoId, FILA_CON_FECHA_ROTA, "FECHA CIRUGIA", "2026-01-11");

        datasetClinicoService.descartarBorrador(escenario.datasetId());

        assertThat(datasetClinicoRepository.findById(escenario.datasetId())).isEmpty();
        assertThat(importacionTrabajoRepository.findById(trabajoId)).isEmpty();
        assertThat(filaImportacionTrabajoRepository.countByImportacionTrabajoId(trabajoId)).isZero();
        assertThat(campoClinicoRepository.findByDatasetId(escenario.datasetId())).isEmpty();
    }

    // ------------------------------------------------------------------
    // W–X: las protecciones de unicidad siguen en pie
    // ------------------------------------------------------------------

    /** W: crear a mano un campo con un código ya usado sigue siendo un error. */
    @Test
    void crearCampoDuplicadoAMano_sigueFallando() {
        Escenario escenario = prepararEscenario("DUPLICADO_CAMPO");
        campoClinicoService.asegurarParaImportacion(escenario.datasetId(), columnasDelAsistente());

        assertThatThrownBy(() -> campoClinicoService.crear(
                escenario.datasetId(), campoRequest("pacienteCodigo", "Otro HC", TipoDatoExcel.TEXTO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pacienteCodigo");
    }

    /** X: un código de dataset ya ocupado sigue rechazándose. */
    @Test
    void crearDatasetConCodigoYaUsado_sigueFallando() {
        Escenario escenario = prepararEscenario("DUPLICADO_DATASET");
        String codigo = datasetClinicoRepository.findById(escenario.datasetId()).orElseThrow().getCodigo();

        DatasetClinicoRequestDto request = new DatasetClinicoRequestDto();
        request.setCodigo(codigo);
        request.setNombre("Otro dataset con el mismo código");
        request.setEstadoDataset(EstadoDatasetClinico.BORRADOR);

        assertThatThrownBy(() -> datasetClinicoService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(codigo);
    }
}
