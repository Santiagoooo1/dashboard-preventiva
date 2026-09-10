package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.PropuestaWidgetDto;
import com.preventiva.backend.dto.PuntoSerieDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;
import com.preventiva.backend.dto.SerieTemporalRequestDto;
import com.preventiva.backend.dto.SerieTemporalResponseDto;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.service.interfaces.BloqueInicialIlqService;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.MetricaAnaliticaService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import com.preventiva.backend.util.BloqueInicialIlq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Fase 6.9L — los cinco indicadores que deben abrir el dashboard inicial de un
 * dataset de infección quirúrgica.
 *
 * <p>Nace de una petición del médico que está probando la aplicación: el
 * dashboard automático abría con «Total de registros», «Pacientes únicos» y
 * «Edad media», y «Localización de la infección» no llegaba a aparecer nunca.
 *
 * <p>El caso que más importa es el de la localización: es un campo casi vacío
 * —quien no se infecta no tiene localización— y aun así es de los primeros que
 * hay que ver, porque se mira dentro de los casos con ILQ.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BloqueInicialIlqIntegrationTest {

    private static final String PREFIJO = "TEST_69L_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private MetricaClinicaService metricaClinicaService;
    @Autowired private MetricaAnaliticaService metricaAnaliticaService;
    @Autowired private BloqueInicialIlqService bloqueInicialIlqService;

    private static final String C_ILQ = "infeccionLocalizacionQuirurgica";
    private static final String C_LOCALIZACION = "localizacionInfeccion";
    private static final String C_ADECUACION = "adecuacionProfilaxis";
    private static final String C_MOTIVO = "motivoInadecuacionProfilaxis";

    private static final String COD_CASOS = BloqueInicialIlq.PREFIJO + "casos";
    private static final String COD_TASA = BloqueInicialIlq.PREFIJO + "tasa";
    private static final String COD_LOCALIZACION = BloqueInicialIlq.PREFIJO + "localizacion";
    private static final String COD_ADECUACION = BloqueInicialIlq.PREFIJO + "adecuacion";
    private static final String COD_MOTIVOS = BloqueInicialIlq.PREFIJO + "motivos_inadecuacion";
    private static final String COD_TASA_MENSUAL = BloqueInicialIlq.PREFIJO + "tasa_mensual";
    private static final String COD_DETALLE = BloqueInicialIlq.PREFIJO + "detalle_mensual";

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre("Test 69L");
        return datasetClinicoService.crear(r).getId();
    }

    private void crearCampo(Long datasetId, String codigo, TipoDatoExcel tipo, boolean comun) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(codigo);
        r.setTipoDato(tipo);
        r.setEsComun(comun);
        r.setObligatorio(false);
        campoClinicoService.crear(datasetId, r);
    }

    /** Los campos que siempre están, sean cuales sean los clínicos del test. */
    private Long datasetBase() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo(datasetId, "procedimiento", TipoDatoExcel.TEXTO, true);
        return datasetId;
    }

    /** Dataset con los cuatro campos clínicos del bloque. */
    private Long datasetIlqCompleto() {
        Long datasetId = datasetBase();
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, C_LOCALIZACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_ADECUACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_MOTIVO, TipoDatoExcel.TEXTO, false);
        return datasetId;
    }

    private void registro(
            Long datasetId, String paciente, LocalDate fecha, String ilq,
            String localizacion, String adecuacion, String motivo) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setFechaEvento(fecha);
        r.setProcedimiento("COLECISTECTOMIA");

        Map<String, Object> d = new HashMap<>();
        if (ilq != null) d.put(C_ILQ, ilq);
        if (localizacion != null) d.put(C_LOCALIZACION, localizacion);
        if (adecuacion != null) d.put(C_ADECUACION, adecuacion);
        if (motivo != null) d.put(C_MOTIVO, motivo);
        r.setDatosDinamicos(d);

        registroClinicoGenericoService.crear(r);
    }

    private PropuestaWidgetDto buscar(List<PropuestaWidgetDto> propuestas, String codigo) {
        return propuestas.stream()
                .filter(p -> codigo.equals(p.getCodigoMetrica()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se propuso el widget " + codigo));
    }

    /** Crea la métrica propuesta y la ejecuta, para comprobar lo que devuelve de verdad. */
    private ResultadoMetricaResponseDto ejecutar(Long datasetId, PropuestaWidgetDto propuesta) {
        return metricaClinicaService.ejecutar(crearMetrica(datasetId, propuesta).getId(), null);
    }

    private com.preventiva.backend.dto.MetricaClinicaResponseDto crearMetrica(
            Long datasetId, PropuestaWidgetDto propuesta) {
        MetricaClinicaRequestDto r = new MetricaClinicaRequestDto();
        r.setCodigo(propuesta.getCodigoMetrica());
        r.setNombre(propuesta.getNombre());
        r.setDescripcion(propuesta.getDescripcion());
        r.setTipoMetrica(com.preventiva.backend.enums.TipoMetrica.valueOf(propuesta.getTipoMetrica()));
        r.setConfiguracion(propuesta.getConfiguracion());
        r.setUnidad(propuesta.getUnidad());
        r.setDecimales(propuesta.getDecimales());
        return metricaClinicaService.crear(datasetId, r);
    }

    // ------------------------------------------------------------------
    // A: el bloque completo
    // ------------------------------------------------------------------

    /** A: con los cuatro campos clínicos se proponen los cinco widgets, en orden. */
    @Test
    void datasetIlqCompleto_proponeLosCincoWidgetsPrioritariosEnOrden() {
        Long datasetId = datasetIlqCompleto();

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica)
                .containsExactly(COD_CASOS, COD_TASA, COD_LOCALIZACION, COD_ADECUACION, COD_MOTIVOS,
                        COD_TASA_MENSUAL, COD_DETALLE);
        assertThat(propuestas).extracting(PropuestaWidgetDto::getNombre)
                .containsExactly(
                        "Casos de ILQ",
                        "Tasa de ILQ",
                        "Localización de la infección",
                        "Adecuación de profilaxis",
                        "Motivos de inadecuación",
                        "Evolución mensual de infección de localización quirúrgica",
                        "Detalle mensual de ILQ");
        assertThat(propuestas).extracting(PropuestaWidgetDto::getOrden).containsExactly(1, 2, 3, 4, 5, 6, 7);

        // B: el recuento va primero y es un CONTEO, no un porcentaje.
        assertThat(propuestas.get(0).getTipoMetrica()).isEqualTo("CONTEO");
        assertThat(propuestas.get(0).getTipoVisualizacion()).isEqualTo("KPI");
        // El KPI de tasa es porcentual; la evolución ocupa el ancho completo.
        assertThat(propuestas.get(1).getTipoMetrica()).isEqualTo("PORCENTAJE");
        assertThat(buscar(propuestas, COD_TASA).getUnidad()).isEqualTo("%");
        assertThat(buscar(propuestas, COD_TASA).getTipoVisualizacion()).isEqualTo("KPI");
        assertThat(buscar(propuestas, COD_TASA_MENSUAL).getAncho()).isEqualTo(12);
        assertThat(buscar(propuestas, COD_TASA_MENSUAL).getTipoVisualizacion()).isEqualTo("LINEAS");
    }

    /** C: «Casos de ILQ» cuenta las intervenciones con infección, sin más. */
    @Test
    void casosDeIlq_cuentaSoloLasIntervencionesConInfeccion() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "SI", "ORGANO_ESPACIO", "ADECUADA", null);
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "SI", "PROFUNDA", "ADECUADA", null);
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), "NO", null, "ADECUADA", null);
        registro(datasetId, "P4", LocalDate.of(2026, 1, 8), "NO", null, "ADECUADA", null);
        registro(datasetId, "P5", LocalDate.of(2026, 1, 9), null, null, "ADECUADA", null);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        ResultadoMetricaResponseDto casos = ejecutar(datasetId, buscar(propuestas, COD_CASOS));

        assertThat(casos.getValor()).isEqualTo(2.0);
    }

    /**
     * D y E: la tasa divide por las documentadas, no por todas. El registro sin
     * documentar no es una intervención sin infección.
     */
    @Test
    void tasaDeIlq_divideEntreLasDocumentadasYNoCuentaElNuloComoNo() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "SI", "ORGANO_ESPACIO", "ADECUADA", null);
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "NO", null, "ADECUADA", null);
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), "NO", null, "ADECUADA", null);
        registro(datasetId, "P4", LocalDate.of(2026, 1, 8), "NO", null, "ADECUADA", null);
        // Sin documentar: fuera del denominador. Con él serían 5 y la tasa 20 %.
        registro(datasetId, "P5", LocalDate.of(2026, 1, 9), null, null, "ADECUADA", null);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        ResultadoMetricaResponseDto tasa = ejecutar(datasetId, buscar(propuestas, COD_TASA));

        assertThat(tasa.getTotalNumerador()).isEqualTo(1);
        assertThat(tasa.getTotalDenominador()).as("4 documentadas, no 5").isEqualTo(4);
        assertThat(tasa.getValor()).isCloseTo(25.0, within(0.01));
    }

    // ------------------------------------------------------------------
    // B–C: la localización, que es lo que faltaba
    // ------------------------------------------------------------------

    /**
     * B: el campo está casi vacío —96 de 100 registros sin localización, porque
     * no se infectaron— y aun así se propone. Descartarlo por completitud
     * global es el error que dejaba fuera este widget.
     */
    @Test
    void localizacionCasiVacia_seProponeIgualmente() {
        Long datasetId = datasetIlqCompleto();
        for (int i = 1; i <= 96; i++) {
            registro(datasetId, "P" + i, LocalDate.of(2026, 1, 10), "NO", null, "ADECUADA", null);
        }
        for (int i = 97; i <= 100; i++) {
            registro(datasetId, "P" + i, LocalDate.of(2026, 1, 10), "SI", "INCISIONAL_SUPERFICIAL",
                    "INADECUADA", "DOSIS_INADECUADA");
        }

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica).contains(COD_LOCALIZACION);
        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, buscar(propuestas, COD_LOCALIZACION));
        assertThat(resultado.getItems()).isNotEmpty();
    }

    /** C: la localización solo cuenta los casos con ILQ; los nulos no entran. */
    @Test
    void localizacion_soloCuentaLosCasosConInfeccion() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "SI", "ORGANO_ESPACIO", "ADECUADA", null);
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "SI", "ORGANO_ESPACIO", "ADECUADA", null);
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), "SI", "INCISIONAL_PROFUNDA", "ADECUADA", null);
        // Sin ILQ: no debe aportar categoría, ni siquiera si trae localización
        // por un error de captura.
        registro(datasetId, "P4", LocalDate.of(2026, 1, 8), "NO", null, "ADECUADA", null);
        registro(datasetId, "P5", LocalDate.of(2026, 1, 9), "NO", "INCISIONAL_SUPERFICIAL", "ADECUADA", null);
        registro(datasetId, "P6", LocalDate.of(2026, 1, 10), null, null, "ADECUADA", null);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, buscar(propuestas, COD_LOCALIZACION));

        assertThat(resultado.getItems()).extracting(i -> i.getEtiqueta())
                .containsExactlyInAnyOrder("ORGANO_ESPACIO", "INCISIONAL_PROFUNDA");
        long total = resultado.getItems().stream().mapToLong(i -> i.getValor().longValue()).sum();
        assertThat(total).as("solo los 3 casos con ILQ").isEqualTo(3);
        assertThat(resultado.getItems()).noneMatch(i -> "INCISIONAL_SUPERFICIAL".equals(i.getEtiqueta()));
    }

    /**
     * G: con tres localizaciones distintas salen exactamente tres categorías,
     * no un Sí/No. Son los valores del Excel real que motivó la corrección.
     */
    @Test
    void localizacion_devuelveLasCategoriasRealesYNoSiNo() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "SI", "ÓRGANO/ESPACIO", "ADECUADA", null);
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "SI", "ÓRGANO/ESPACIO", "ADECUADA", null);
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), "SI", "PROFUNDA", "ADECUADA", null);
        registro(datasetId, "P4", LocalDate.of(2026, 1, 8), "SI", "INCISIONAL SUPERFICIAL", "ADECUADA", null);
        registro(datasetId, "P5", LocalDate.of(2026, 1, 9), "NO", null, "ADECUADA", null);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, buscar(propuestas, COD_LOCALIZACION));

        assertThat(resultado.getItems()).hasSize(3);
        assertThat(resultado.getItems()).extracting(i -> i.getEtiqueta())
                .containsExactlyInAnyOrder("ÓRGANO/ESPACIO", "PROFUNDA", "INCISIONAL SUPERFICIAL");
        assertThat(resultado.getItems()).extracting(i -> i.getEtiqueta())
                .doesNotContain("SI", "NO", "true", "false");
    }

    // ------------------------------------------------------------------
    // D–E: profilaxis
    // ------------------------------------------------------------------

    /** D: los motivos solo salen de las profilaxis inadecuadas. */
    @Test
    void motivos_soloCuentanLasProfilaxisInadecuadas() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "NO", null, "INADECUADA", "DOSIS_INADECUADA");
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "NO", null, "INADECUADA", "DOSIS_INADECUADA");
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), "NO", null, "INADECUADA", "MOMENTO_INADECUADO");
        // Un motivo anotado sobre una profilaxis correcta no es un fallo que
        // corregir: no debe contaminar el gráfico.
        registro(datasetId, "P4", LocalDate.of(2026, 1, 8), "NO", null, "ADECUADA", "ANTIBIOTICO_INCORRECTO");
        registro(datasetId, "P5", LocalDate.of(2026, 1, 9), "NO", null, "NO_APLICA", "ANTIBIOTICO_INCORRECTO");

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, buscar(propuestas, COD_MOTIVOS));

        assertThat(resultado.getItems()).extracting(i -> i.getEtiqueta())
                .containsExactlyInAnyOrder("DOSIS_INADECUADA", "MOMENTO_INADECUADO");
        assertThat(resultado.getItems()).noneMatch(i -> "ANTIBIOTICO_INCORRECTO".equals(i.getEtiqueta()));
        long total = resultado.getItems().stream().mapToLong(i -> i.getValor().longValue()).sum();
        assertThat(total).isEqualTo(3);
    }

    /** E: la adecuación conserva sus tres categorías; NO_APLICA no se funde con INADECUADA. */
    @Test
    void adecuacion_conservaAdecuadaInadecuadaYNoAplica() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "NO", null, "ADECUADA", null);
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "NO", null, "ADECUADA", null);
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), "NO", null, "INADECUADA", "DOSIS_INADECUADA");
        registro(datasetId, "P4", LocalDate.of(2026, 1, 8), "NO", null, "NO_APLICA", null);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, buscar(propuestas, COD_ADECUACION));

        assertThat(resultado.getItems()).extracting(i -> i.getEtiqueta())
                .containsExactlyInAnyOrder("ADECUADA", "INADECUADA", "NO_APLICA");
        assertThat(resultado.getItems()).filteredOn(i -> "NO_APLICA".equals(i.getEtiqueta()))
                .singleElement()
                .satisfies(i -> assertThat(i.getValor().longValue()).isEqualTo(1));
    }

    /**
     * Los motivos funcionan también con el vocabulario del Excel real, que
     * escribe SI/NO en lugar de ADECUADA/INADECUADA. Con un solo valor
     * esperado, este widget salía vacío en ese hospital sin decir por qué.
     */
    @Test
    void motivos_reconocenTambienElVocabularioSiNoDeLosArchivosReales() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "NO", null, "NO", "INICIO");
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "NO", null, "NO", "INICIO");
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), "NO", null, "NO", "INDICACION (+dosis)");
        // "SI" = profilaxis adecuada: su motivo no debe contarse.
        registro(datasetId, "P4", LocalDate.of(2026, 1, 8), "NO", null, "SI", "INICIO");

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, buscar(propuestas, COD_MOTIVOS));

        assertThat(resultado.getItems()).extracting(i -> i.getEtiqueta())
                .containsExactlyInAnyOrder("INICIO", "INDICACION (+dosis)");
        long total = resultado.getItems().stream().mapToLong(i -> i.getValor().longValue()).sum();
        assertThat(total).as("solo las 3 inadecuadas").isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // F: la evolución mensual es una tasa, no un recuento
    // ------------------------------------------------------------------

    /**
     * F: el caso del enunciado. Enero: 3 de 100 → 3 %. Febrero: 2 de 50 → 4 %.
     *
     * <p>Contando casos la línea bajaría de 3 a 2, sugiriendo mejora; en tasa
     * sube de 3 % a 4 %, que es lo que de verdad ocurre. Es la diferencia entre
     * leer bien y leer al revés.
     */
    @Test
    void evolucionMensual_devuelvePorcentajeYNoRecuento() {
        Long datasetId = datasetIlqCompleto();
        for (int i = 1; i <= 97; i++) {
            registro(datasetId, "E" + i, LocalDate.of(2026, 1, 10), "NO", null, "ADECUADA", null);
        }
        for (int i = 98; i <= 100; i++) {
            registro(datasetId, "E" + i, LocalDate.of(2026, 1, 10), "SI", "ORGANO_ESPACIO", "ADECUADA", null);
        }
        for (int i = 1; i <= 48; i++) {
            registro(datasetId, "F" + i, LocalDate.of(2026, 2, 10), "NO", null, "ADECUADA", null);
        }
        for (int i = 49; i <= 50; i++) {
            registro(datasetId, "F" + i, LocalDate.of(2026, 2, 10), "SI", "ORGANO_ESPACIO", "ADECUADA", null);
        }

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        PropuestaWidgetDto evolucion = buscar(propuestas, COD_TASA_MENSUAL);

        assertThat(evolucion.getTipoMetrica()).isEqualTo("PORCENTAJE");
        assertThat(evolucion.getTipoResultado()).isEqualTo("SERIE_TEMPORAL");
        assertThat(evolucion.getConfiguracionWidget().getGranularidad()).isEqualTo(Granularidad.MES);

        SerieTemporalRequestDto peticion = new SerieTemporalRequestDto();
        peticion.setGranularidad(Granularidad.MES);
        SerieTemporalResponseDto serie = metricaAnaliticaService.serieTemporal(
                crearMetrica(datasetId, evolucion).getId(), peticion);

        assertThat(serie.getPuntos()).hasSize(2);
        PuntoSerieDto enero = serie.getPuntos().get(0);
        PuntoSerieDto febrero = serie.getPuntos().get(1);

        assertThat(enero.getValor()).isCloseTo(3.0, within(0.01));
        assertThat(enero.getTotalNumerador()).isEqualTo(3);
        assertThat(enero.getTotalDenominador()).isEqualTo(100);

        assertThat(febrero.getValor()).isCloseTo(4.0, within(0.01));
        assertThat(febrero.getTotalNumerador()).isEqualTo(2);
        assertThat(febrero.getTotalDenominador()).isEqualTo(50);

        // Lo esencial: en tasa la línea SUBE, aunque haya menos casos.
        assertThat(febrero.getValor()).isGreaterThan(enero.getValor());
    }

    /** F (complemento): los registros sin ILQ documentada no entran en el denominador. */
    @Test
    void evolucionMensual_noCuentaComoSanoLoQueNoEstaDocumentado() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "SI", "ORGANO_ESPACIO", "ADECUADA", null);
        registro(datasetId, "P2", LocalDate.of(2026, 1, 6), "NO", null, "ADECUADA", null);
        // Sin documentar: no es una intervención sin infección, es una de la que
        // no se sabe. Si contara, la tasa bajaría de 50 % a 33 %.
        registro(datasetId, "P3", LocalDate.of(2026, 1, 7), null, null, "ADECUADA", null);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        SerieTemporalRequestDto peticion = new SerieTemporalRequestDto();
        peticion.setGranularidad(Granularidad.MES);
        SerieTemporalResponseDto serie = metricaAnaliticaService.serieTemporal(
                crearMetrica(datasetId, buscar(propuestas, COD_TASA_MENSUAL)).getId(), peticion);

        assertThat(serie.getPuntos()).hasSize(1);
        assertThat(serie.getPuntos().get(0).getTotalDenominador()).isEqualTo(2);
        assertThat(serie.getPuntos().get(0).getValor()).isCloseTo(50.0, within(0.01));
    }

    // ------------------------------------------------------------------
    // G–H: qué pasa con "Registros por mes"
    // ------------------------------------------------------------------

    /**
     * G: cuando hay evolución clínica, el bloque trae su propia serie temporal.
     * El generador del dashboard usa justamente eso para no añadir además
     * «Registros por mes», que ocuparía el mismo hueco diciendo menos.
     */
    @Test
    void datasetIlq_traeUnaSerieTemporalPropia() {
        Long datasetId = datasetIlqCompleto();

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        // Dos series temporales: la gráfica y su tabla de detalle. Ambas son
        // PORCENTAJE, así que «Registros por mes» sigue sin hacer falta.
        assertThat(propuestas).filteredOn(p -> "SERIE_TEMPORAL".equals(p.getTipoResultado()))
                .hasSize(2)
                .allSatisfy(p -> assertThat(p.getTipoMetrica()).isEqualTo("PORCENTAJE"))
                .extracting(com.preventiva.backend.dto.PropuestaWidgetDto::getCodigoMetrica)
                .containsExactly(COD_TASA_MENSUAL, COD_DETALLE);
        assertThat(propuestas).filteredOn(p -> "TABLA".equals(p.getTipoVisualizacion()))
                .singleElement()
                .satisfies(p -> assertThat(p.getAncho()).isEqualTo(12));
    }

    /**
     * H: un dataset que no habla de infección quirúrgica no recibe bloque
     * clínico, y el dashboard se genera solo con las reglas genéricas —incluido
     * «Registros por mes»—.
     */
    @Test
    void datasetSinCampoDeInfeccion_noRecibeBloqueClinico() {
        Long datasetId = datasetBase();
        crearCampo(datasetId, "sexo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "edad", TipoDatoExcel.ENTERO, true);

        assertThat(bloqueInicialIlqService.proponerBloqueInicial(datasetId)).isEmpty();
    }

    /** H (variante): tener profilaxis pero no infección tampoco activa el bloque. */
    @Test
    void datasetConProfilaxisPeroSinInfeccion_noRecibeBloqueClinico() {
        Long datasetId = datasetBase();
        crearCampo(datasetId, C_ADECUACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_MOTIVO, TipoDatoExcel.TEXTO, false);

        assertThat(bloqueInicialIlqService.proponerBloqueInicial(datasetId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // I: el bloque no compite por el cupo
    // ------------------------------------------------------------------

    /**
     * I: por muchas columnas que traiga el dataset, el bloque sigue siendo el
     * mismo y completo. El tope de widgets recorta lo genérico, nunca esto.
     */
    @Test
    void datasetConMuchasColumnas_conservaLosCincoPrioritarios() {
        Long datasetId = datasetIlqCompleto();
        for (int i = 1; i <= 25; i++) {
            crearCampo(datasetId, "columnaExtra" + i, TipoDatoExcel.TEXTO, false);
        }

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).hasSize(7);
        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica)
                .containsExactly(COD_CASOS, COD_TASA, COD_LOCALIZACION, COD_ADECUACION, COD_MOTIVOS,
                        COD_TASA_MENSUAL, COD_DETALLE);
    }

    // ------------------------------------------------------------------
    // J–K: ausencias parciales
    // ------------------------------------------------------------------

    /** J: sin localización se pierde ese widget y solo ese. */
    @Test
    void sinLocalizacion_seOmiteEseWidgetYSeConservanLosDemas() {
        Long datasetId = datasetBase();
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, C_ADECUACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_MOTIVO, TipoDatoExcel.TEXTO, false);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica)
                .containsExactly(COD_CASOS, COD_TASA, COD_ADECUACION, COD_MOTIVOS, COD_TASA_MENSUAL, COD_DETALLE);
        assertThat(propuestas).extracting(PropuestaWidgetDto::getOrden).containsExactly(1, 2, 3, 4, 5, 6);
    }

    /** K: sin motivos se pierde ese widget y solo ese. */
    @Test
    void sinMotivoInadecuacion_seOmiteEseWidgetYSeConservanLosDemas() {
        Long datasetId = datasetBase();
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, C_LOCALIZACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_ADECUACION, TipoDatoExcel.TEXTO, false);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica)
                .containsExactly(COD_CASOS, COD_TASA, COD_LOCALIZACION, COD_ADECUACION, COD_TASA_MENSUAL, COD_DETALLE);
    }

    /** K (variante): sin fechaEvento no hay evolución mensual, pero sí el resto. */
    @Test
    void sinFechaEvento_seOmiteLaEvolucionMensual() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, C_LOCALIZACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_ADECUACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_MOTIVO, TipoDatoExcel.TEXTO, false);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica)
                .containsExactly(COD_CASOS, COD_TASA, COD_LOCALIZACION, COD_ADECUACION, COD_MOTIVOS);
        assertThat(propuestas).noneMatch(p -> "SERIE_TEMPORAL".equals(p.getTipoResultado()));
    }

    /** Solo el campo de infección: se propone lo que depende de él y nada más. */
    @Test
    void soloCampoDeInfeccion_proponeTasaYEvolucion() {
        Long datasetId = datasetBase();
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica)
                .containsExactly(COD_CASOS, COD_TASA, COD_TASA_MENSUAL, COD_DETALLE);
    }

    // ------------------------------------------------------------------
    // L: repetir la generación
    // ------------------------------------------------------------------

    /** L: pedir el bloque dos veces devuelve exactamente lo mismo y no crea nada. */
    @Test
    void pedirElBloqueDosVeces_devuelveLoMismoYNoCreaNada() {
        Long datasetId = datasetIlqCompleto();
        registro(datasetId, "P1", LocalDate.of(2026, 1, 5), "SI", "ORGANO_ESPACIO", "INADECUADA", "DOSIS_INADECUADA");

        List<PropuestaWidgetDto> primera = bloqueInicialIlqService.proponerBloqueInicial(datasetId);
        List<PropuestaWidgetDto> segunda = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(segunda).extracting(PropuestaWidgetDto::getCodigoMetrica)
                .isEqualTo(primera.stream().map(PropuestaWidgetDto::getCodigoMetrica).toList());
        assertThat(segunda).extracting(PropuestaWidgetDto::getOrden)
                .isEqualTo(primera.stream().map(PropuestaWidgetDto::getOrden).toList());

        // Los códigos son únicos dentro del bloque: nada podría duplicarse al
        // crearlo.
        assertThat(primera).extracting(PropuestaWidgetDto::getCodigoMetrica).doesNotHaveDuplicates();

        // Y es una consulta: no ha aparecido ninguna métrica en el dataset.
        assertThat(metricaClinicaService.listarPorDataset(datasetId)).isEmpty();
    }

    /** Un campo marcado como EXCLUIR es una decisión del usuario y se respeta. */
    @Test
    void campoMarcadoComoExcluir_noGeneraSuWidget() {
        Long datasetId = datasetIlqCompleto();
        var campos = campoClinicoService.listarPorDataset(datasetId);
        var localizacion = campos.stream()
                .filter(c -> C_LOCALIZACION.equals(c.getCodigo()))
                .findFirst()
                .orElseThrow();

        CampoClinicoRequestDto actualizacion = new CampoClinicoRequestDto();
        actualizacion.setCodigo(localizacion.getCodigo());
        actualizacion.setEtiqueta(localizacion.getEtiqueta());
        actualizacion.setTipoDato(TipoDatoExcel.TEXTO);
        actualizacion.setEsComun(false);
        actualizacion.setObligatorio(false);
        actualizacion.setPrioridadDashboard(com.preventiva.backend.enums.PrioridadDashboardCampo.EXCLUIR);
        campoClinicoService.actualizar(datasetId, localizacion.getId(), actualizacion);

        List<PropuestaWidgetDto> propuestas = bloqueInicialIlqService.proponerBloqueInicial(datasetId);

        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica).doesNotContain(COD_LOCALIZACION);
        assertThat(propuestas).extracting(PropuestaWidgetDto::getCodigoMetrica).contains(COD_TASA);
    }
}
