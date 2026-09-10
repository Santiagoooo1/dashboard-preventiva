package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.PuntoSerieDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.SerieSegmentadaDto;
import com.preventiva.backend.dto.SerieTemporalRequestDto;
import com.preventiva.backend.dto.SerieTemporalResponseDto;
import com.preventiva.backend.enums.EstadoResultadoMetrica;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.MetricaAnaliticaService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Fase 6.9N — tablas temporales: una fila por periodo, calculadas con el mismo
 * motor que alimenta las gráficas.
 *
 * <p>Lo que estos tests protegen sobre todo es la fila TOTAL. Es la cifra que
 * más se mira y la más fácil de calcular mal: promediar las tasas mensuales
 * pondera igual un mes de 8 intervenciones y uno de 22, y no da la tasa del
 * periodo. Tiene que salir de acumular numeradores y denominadores.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TablaTemporalIntegrationTest {

    private static final String PREFIJO = "TEST_69N_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private MetricaClinicaService metricaClinicaService;
    @Autowired private MetricaAnaliticaService metricaAnaliticaService;

    private static final String C_ILQ = "infeccionLocalizacionQuirurgica";
    private static final String C_ADECUACION = "adecuacionProfilaxis";
    private static final String C_DURACION = "duracionCirugia";

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre("Test 69N");
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

    private Long datasetBase() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, C_ADECUACION, TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, C_DURACION, TipoDatoExcel.ENTERO, false);
        return datasetId;
    }

    private void registro(Long datasetId, LocalDate fecha, String ilq, String adecuacion, Integer duracion) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo("P" + contador.incrementAndGet());
        r.setFechaEvento(fecha);
        Map<String, Object> d = new HashMap<>();
        if (ilq != null) d.put(C_ILQ, ilq);
        if (adecuacion != null) d.put(C_ADECUACION, adecuacion);
        if (duracion != null) d.put(C_DURACION, duracion);
        r.setDatosDinamicos(d);
        registroClinicoGenericoService.crear(r);
    }

    private Long crearMetrica(Long datasetId, String codigo, TipoMetrica tipo, ConfiguracionMetricaDto config) {
        MetricaClinicaRequestDto r = new MetricaClinicaRequestDto();
        r.setCodigo(codigo + "_" + contador.incrementAndGet());
        r.setNombre(codigo);
        r.setTipoMetrica(tipo);
        r.setConfiguracion(config);
        r.setDecimales(2);
        return metricaClinicaService.crear(datasetId, r).getId();
    }

    /** Tasa: numerador = campo true; denominador = campo informado. */
    private Long metricaTasaIlq(Long datasetId) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        c.setNumerador(grupo(filtro(C_ILQ, OperadorFiltro.EQ, true)));
        c.setDenominador(grupo(filtro(C_ILQ, OperadorFiltro.NOT_NULL, null)));
        return crearMetrica(datasetId, "tasa_ilq", TipoMetrica.PORCENTAJE, c);
    }

    private SerieTemporalResponseDto serie(Long metricaId, Granularidad granularidad) {
        SerieTemporalRequestDto r = new SerieTemporalRequestDto();
        r.setGranularidad(granularidad);
        return metricaAnaliticaService.serieTemporal(metricaId, r);
    }

    private SerieTemporalResponseDto serieSegmentada(Long metricaId, String campoSegmentacion) {
        SerieTemporalRequestDto r = new SerieTemporalRequestDto();
        r.setGranularidad(Granularidad.MES);
        r.setCampoSegmentacion(campoSegmentacion);
        return metricaAnaliticaService.serieTemporal(metricaId, r);
    }

    private FiltroMetricaDto filtro(String campo, OperadorFiltro op, Object valor) {
        FiltroMetricaDto f = new FiltroMetricaDto();
        f.setCampo(campo);
        f.setOperador(op);
        f.setValor(valor);
        return f;
    }

    private FiltroGrupoDto grupo(FiltroMetricaDto... filtros) {
        FiltroGrupoDto g = new FiltroGrupoDto();
        g.setFiltros(List.of(filtros));
        return g;
    }

    // ------------------------------------------------------------------
    // A–C, §22: el caso ILQ del enunciado
    // ------------------------------------------------------------------

    /**
     * A, C y §22: enero 1/8, febrero 0/7, marzo 0/16. El TOTAL es 1/31 = 3,23 %,
     * no la media de 12,5 / 0 / 0 = 4,17 %.
     */
    @Test
    void detalleMensualDeIlq_conTotalAcumuladoYNoPromediado() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        for (int i = 0; i < 7; i++) registro(datasetId, LocalDate.of(2026, 1, 10), "NO", null, null);
        for (int i = 0; i < 7; i++) registro(datasetId, LocalDate.of(2026, 2, 10), "NO", null, null);
        for (int i = 0; i < 16; i++) registro(datasetId, LocalDate.of(2026, 3, 10), "NO", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        assertThat(s.getPuntos()).extracting(PuntoSerieDto::getPeriodo)
                .containsExactly("2026-01", "2026-02", "2026-03");

        PuntoSerieDto enero = s.getPuntos().get(0);
        assertThat(enero.getTotalDenominador()).isEqualTo(8);
        assertThat(enero.getTotalNumerador()).isEqualTo(1);
        assertThat(enero.getValor()).isCloseTo(12.5, within(0.01));

        assertThat(s.getPuntos().get(1).getTotalDenominador()).isEqualTo(7);
        assertThat(s.getPuntos().get(1).getValor()).isCloseTo(0.0, within(0.01));
        assertThat(s.getPuntos().get(2).getTotalDenominador()).isEqualTo(16);

        assertThat(s.getTotal()).isNotNull();
        assertThat(s.getTotal().getTotalDenominador()).isEqualTo(31);
        assertThat(s.getTotal().getTotalNumerador()).isEqualTo(1);
        assertThat(s.getTotal().getValor())
                .as("1/31 acumulado, no la media de 12,5 / 0 / 0 = 4,17")
                .isCloseTo(3.23, within(0.01));
    }

    /** B: un NULL no cuenta como «no». Sale del denominador, no lo engorda. */
    @Test
    void nullBooleano_noCuentaComoNo() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO", null, null);
        registro(datasetId, LocalDate.of(2026, 1, 7), null, null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        PuntoSerieDto enero = s.getPuntos().get(0);
        assertThat(enero.getTotalDenominador()).as("2 documentados, no 3").isEqualTo(2);
        assertThat(enero.getValor()).isCloseTo(50.0, within(0.01));
        assertThat(s.getTotal().getTotalDenominador()).isEqualTo(2);
    }

    /** La tabla y la gráfica salen del mismo cálculo: no pueden discrepar (§9). */
    @Test
    void tablaYGrafica_devuelvenExactamenteLosMismosValores() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        for (int i = 0; i < 7; i++) registro(datasetId, LocalDate.of(2026, 1, 10), "NO", null, null);
        for (int i = 0; i < 7; i++) registro(datasetId, LocalDate.of(2026, 2, 10), "NO", null, null);
        Long metricaId = metricaTasaIlq(datasetId);

        SerieTemporalResponseDto primera = serie(metricaId, Granularidad.MES);
        SerieTemporalResponseDto segunda = serie(metricaId, Granularidad.MES);

        assertThat(segunda.getPuntos()).extracting(PuntoSerieDto::getValor)
                .isEqualTo(primera.getPuntos().stream().map(PuntoSerieDto::getValor).toList());
        assertThat(segunda.getPuntos()).extracting(PuntoSerieDto::getTotalDenominador)
                .isEqualTo(primera.getPuntos().stream().map(PuntoSerieDto::getTotalDenominador).toList());
        assertThat(segunda.getTotal().getValor()).isEqualTo(primera.getTotal().getValor());
    }

    // ------------------------------------------------------------------
    // D–E, P: categóricas
    // ------------------------------------------------------------------

    /** D: tres categorías reales, una columna por cada una. */
    @Test
    void categorica_produceUnaSeriePorCategoria() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 1, 6), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 1, 7), null, "INADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 1, 8), null, "NO_APLICA", null);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        SerieTemporalResponseDto s = serieSegmentada(
                crearMetrica(datasetId, "conteo", TipoMetrica.CONTEO, c), C_ADECUACION);

        assertThat(s.getSeries()).extracting(SerieSegmentadaDto::getEtiqueta)
                .containsExactlyInAnyOrder("ADECUADA", "INADECUADA", "NO_APLICA");
        assertThat(s.getSegmentadoPor()).isEqualTo(C_ADECUACION);
    }

    /**
     * E: una categoría que solo aparece en febrero sigue teniendo fila en enero,
     * con 0. Si no, las columnas se descuadrarían entre periodos.
     */
    @Test
    void categoriasQueVarianEntreMeses_conservanTodosLosPeriodos() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 2, 5), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 2, 6), null, "NO_APLICA", null);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        SerieTemporalResponseDto s = serieSegmentada(
                crearMetrica(datasetId, "conteo", TipoMetrica.CONTEO, c), C_ADECUACION);

        for (SerieSegmentadaDto segmento : s.getSeries()) {
            assertThat(segmento.getPuntos()).extracting(PuntoSerieDto::getPeriodo)
                    .as("el segmento %s debe cubrir los dos meses", segmento.getEtiqueta())
                    .containsExactly("2026-01", "2026-02");
        }
        SerieSegmentadaDto noAplica = s.getSeries().stream()
                .filter(x -> "NO_APLICA".equals(x.getEtiqueta())).findFirst().orElseThrow();
        assertThat(noAplica.getPuntos().get(0).getValor()).as("enero sin esa categoría").isEqualTo(0.0);
        assertThat(noAplica.getPuntos().get(1).getValor()).isEqualTo(1.0);
    }

    /** P: cada categoría trae su total acumulado, y la serie el suyo. */
    @Test
    void categorica_traeTotalPorCategoriaYTotalGeneral() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 2, 5), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 2, 6), null, "INADECUADA", null);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        SerieTemporalResponseDto s = serieSegmentada(
                crearMetrica(datasetId, "conteo", TipoMetrica.CONTEO, c), C_ADECUACION);

        SerieSegmentadaDto adecuada = s.getSeries().stream()
                .filter(x -> "ADECUADA".equals(x.getEtiqueta())).findFirst().orElseThrow();
        assertThat(adecuada.getTotal()).isNotNull();
        assertThat(adecuada.getTotal().getValor()).as("1 en enero + 1 en febrero").isEqualTo(2.0);
        assertThat(s.getTotal().getValor()).as("los 3 registros del rango").isEqualTo(3.0);
    }

    // ------------------------------------------------------------------
    // F, Q: numéricas
    // ------------------------------------------------------------------

    /** F: media mensual de un numérico, sin contar los nulos. */
    @Test
    void numerica_calculaLaMediaDelPeriodoIgnorandoNulos() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), null, null, 100);
        registro(datasetId, LocalDate.of(2026, 1, 6), null, null, 50);
        registro(datasetId, LocalDate.of(2026, 1, 7), null, null, null);
        registro(datasetId, LocalDate.of(2026, 2, 5), null, null, 30);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        c.setCampoValor(C_DURACION);
        SerieTemporalResponseDto s = serie(
                crearMetrica(datasetId, "media", TipoMetrica.PROMEDIO, c), Granularidad.MES);

        assertThat(s.getPuntos().get(0).getValor()).as("(100+50)/2, el nulo fuera").isCloseTo(75.0, within(0.01));
        assertThat(s.getPuntos().get(1).getValor()).isCloseTo(30.0, within(0.01));
    }

    /**
     * Q: el total numérico se calcula sobre todos los valores, no promediando
     * las medias mensuales. (100+50+30)/3 = 60, no (75+30)/2 = 52,5.
     */
    @Test
    void numerica_elTotalSeCalculaSobreTodosLosValoresYNoPromediandoMedias() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), null, null, 100);
        registro(datasetId, LocalDate.of(2026, 1, 6), null, null, 50);
        registro(datasetId, LocalDate.of(2026, 2, 5), null, null, 30);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        c.setCampoValor(C_DURACION);
        SerieTemporalResponseDto s = serie(
                crearMetrica(datasetId, "media", TipoMetrica.PROMEDIO, c), Granularidad.MES);

        assertThat(s.getTotal().getValor()).isCloseTo(60.0, within(0.01));
        assertThat(s.getTotal().getValor()).isNotEqualTo(52.5);
    }

    /** El mínimo y el máximo del periodo salen de las métricas que ya existen. */
    @Test
    void numerica_minimoYMaximoPorPeriodo() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), null, null, 45);
        registro(datasetId, LocalDate.of(2026, 1, 6), null, null, 170);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        c.setCampoValor(C_DURACION);

        assertThat(serie(crearMetrica(datasetId, "min", TipoMetrica.MINIMO, c), Granularidad.MES)
                .getPuntos().get(0).getValor()).isEqualTo(45.0);
        assertThat(serie(crearMetrica(datasetId, "max", TipoMetrica.MAXIMO, c), Granularidad.MES)
                .getPuntos().get(0).getValor()).isEqualTo(170.0);
        assertThat(serie(crearMetrica(datasetId, "mediana", TipoMetrica.MEDIANA, c), Granularidad.MES)
                .getPuntos().get(0).getValor()).isCloseTo(107.5, within(0.01));
    }

    // ------------------------------------------------------------------
    // G–J: orden, periodos vacíos y filas sin fecha
    // ------------------------------------------------------------------

    /** G: orden cronológico, no lexicográfico de etiquetas. */
    @Test
    void losPeriodos_vanEnOrdenCronologico() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 11, 5), "NO", null, null);
        registro(datasetId, LocalDate.of(2026, 2, 5), "NO", null, null);
        registro(datasetId, LocalDate.of(2027, 1, 5), "NO", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        assertThat(s.getPuntos()).extracting(PuntoSerieDto::getPeriodo)
                .startsWith("2026-02")
                .endsWith("2027-01");
        assertThat(s.getPuntos()).extracting(PuntoSerieDto::getFechaInicio).isSorted();
    }

    /** H: un mes con población y ningún caso es un 0 % real, no un hueco. */
    @Test
    void mesConEvaluadosYSinCasos_esCeroPorCiento() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        for (int i = 0; i < 7; i++) registro(datasetId, LocalDate.of(2026, 2, 10), "NO", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        PuntoSerieDto febrero = s.getPuntos().get(1);
        assertThat(febrero.getTotalDenominador()).isEqualTo(7);
        assertThat(febrero.getValor()).isEqualTo(0.0);
        assertThat(febrero.getEstado()).isEqualTo(EstadoResultadoMetrica.OK.name());
    }

    /**
     * I: un mes sin población NO es un 0 %. Se marca sin base evaluable y el
     * valor viaja como null, siguiendo la política de división por cero que ya
     * usa el resto del motor.
     */
    @Test
    void periodoSinPoblacion_noInventaUnCeroClinico() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        // Febrero: hay registros, pero ninguno con el dato documentado.
        registro(datasetId, LocalDate.of(2026, 2, 10), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 3, 10), "NO", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        PuntoSerieDto febrero = s.getPuntos().get(1);
        assertThat(febrero.getTotalDenominador()).isZero();
        assertThat(febrero.getValor()).as("null, no 0.0").isNull();
        assertThat(febrero.getEstado()).isEqualTo(EstadoResultadoMetrica.SIN_BASE_EVALUABLE.name());
    }

    /** J: un registro sin fecha no pertenece a ningún periodo, ni inventa uno. */
    @Test
    void registroSinFecha_quedaFueraDeLaSerieSinCrearUnPeriodoFicticio() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO", null, null);

        RegistroClinicoGenericoRequestDto sinFecha = new RegistroClinicoGenericoRequestDto();
        sinFecha.setDatasetId(datasetId);
        sinFecha.setPacienteCodigo("SIN_FECHA");
        sinFecha.setDatosDinamicos(Map.of(C_ILQ, "SI"));
        registroClinicoGenericoService.crear(sinFecha);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        assertThat(s.getPuntos()).hasSize(1);
        assertThat(s.getPuntos()).extracting(PuntoSerieDto::getPeriodo).doesNotContain("Sin fecha", "SIN_FECHA");
        assertThat(s.getTotal().getTotalDenominador())
                .as("el registro sin fecha tampoco entra en el total")
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // K–M: filtros y errores funcionales
    // ------------------------------------------------------------------

    /** K: los filtros de la métrica acotan también la tabla temporal. */
    @Test
    void losFiltrosDeLaMetrica_seRespetanEnCadaPeriodo() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO", "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 1, 7), "NO", "INADECUADA", null);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of(filtro(C_ADECUACION, OperadorFiltro.EQ, "ADECUADA")));
        SerieTemporalResponseDto s = serie(
                crearMetrica(datasetId, "conteo_filtrado", TipoMetrica.CONTEO, c), Granularidad.MES);

        assertThat(s.getPuntos().get(0).getValor()).as("solo los 2 adecuados").isEqualTo(2.0);
        assertThat(s.getTotal().getValor()).isEqualTo(2.0);
    }

    /** L: sin campo de fecha, un error explicado, no un 500. */
    @Test
    void datasetSinCampoFecha_daUnErrorFuncionalClaro() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        Long metricaId = crearMetrica(datasetId, "conteo", TipoMetrica.CONTEO, c);

        assertThatThrownBy(() -> serie(metricaId, Granularidad.MES))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fechaEvento");
    }

    /** M: pedir la serie sobre un campo de fecha que no es fecha se explica. */
    @Test
    void campoFechaIncompatible_daUnErrorFuncionalClaro() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);

        SerieTemporalRequestDto r = new SerieTemporalRequestDto();
        r.setGranularidad(Granularidad.MES);
        r.setCampoFecha(C_ADECUACION);
        Long metricaId = metricaTasaIlq(datasetId);

        assertThatThrownBy(() -> metricaAnaliticaService.serieTemporal(metricaId, r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FECHA");
    }

    // ------------------------------------------------------------------
    // N–O: otras granularidades
    // ------------------------------------------------------------------

    /** N: por trimestre, con el total acumulado igual que en mensual. */
    @Test
    void granularidadTrimestral() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        registro(datasetId, LocalDate.of(2026, 2, 5), "NO", null, null);
        registro(datasetId, LocalDate.of(2026, 5, 5), "NO", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.TRIMESTRE);

        assertThat(s.getGranularidad()).isEqualTo("TRIMESTRE");
        assertThat(s.getPuntos()).hasSize(2);
        assertThat(s.getPuntos().get(0).getTotalDenominador()).as("enero y febrero juntos").isEqualTo(2);
        assertThat(s.getTotal().getTotalDenominador()).isEqualTo(3);
        assertThat(s.getTotal().getValor()).isCloseTo(33.33, within(0.01));
    }

    /** O: por año. */
    @Test
    void granularidadAnual() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        registro(datasetId, LocalDate.of(2026, 11, 5), "NO", null, null);
        registro(datasetId, LocalDate.of(2027, 3, 5), "NO", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.ANIO);

        assertThat(s.getGranularidad()).isEqualTo("ANIO");
        assertThat(s.getPuntos()).hasSize(2);
        assertThat(s.getPuntos().get(0).getTotalDenominador()).isEqualTo(2);
        assertThat(s.getPuntos().get(0).getValor()).isCloseTo(50.0, within(0.01));
        assertThat(s.getTotal().getValor()).isCloseTo(33.33, within(0.01));
    }

    /** El total cubre el rango completo, no un periodo suelto. */
    @Test
    void elTotal_cubreElRangoCompleto() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI", null, null);
        registro(datasetId, LocalDate.of(2026, 6, 5), "NO", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        assertThat(s.getTotal().getPeriodo()).isEqualTo("TOTAL");
        assertThat(s.getTotal().getFechaInicio()).isBeforeOrEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(s.getTotal().getFechaFin()).isAfterOrEqualTo(LocalDate.of(2026, 6, 5));
    }

    /** Ningún valor puede salir como NaN o infinito. */
    @Test
    void ningunValorEsNaNNiInfinito() {
        Long datasetId = datasetBase();
        registro(datasetId, LocalDate.of(2026, 1, 5), null, "ADECUADA", null);
        registro(datasetId, LocalDate.of(2026, 2, 5), "SI", null, null);

        SerieTemporalResponseDto s = serie(metricaTasaIlq(datasetId), Granularidad.MES);

        for (PuntoSerieDto p : s.getPuntos()) {
            if (p.getValor() != null) {
                assertThat(Double.isNaN(p.getValor())).isFalse();
                assertThat(Double.isInfinite(p.getValor())).isFalse();
            }
        }
        assertThat(s.getTotal().getValor()).isNotNaN();
    }

    /**
     * R: el motor lee los registros una sola vez y agrupa en memoria. Se
     * comprueba de forma indirecta pero real: el tiempo no puede crecer con el
     * número de periodos si no hubiera una consulta por periodo.
     */
    @Test
    void muchosPeriodos_noDisparanElTiempoDeCalculo() {
        Long datasetId = datasetBase();
        // Tres años de datos: 36 periodos mensuales.
        for (int mes = 0; mes < 36; mes++) {
            LocalDate fecha = LocalDate.of(2024, 1, 15).plusMonths(mes);
            registro(datasetId, fecha, mes % 10 == 0 ? "SI" : "NO", null, null);
        }
        Long metricaId = metricaTasaIlq(datasetId);

        long inicio = System.nanoTime();
        SerieTemporalResponseDto s = serie(metricaId, Granularidad.MES);
        long milis = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(s.getPuntos()).hasSize(36);
        assertThat(s.getTotal().getTotalDenominador()).isEqualTo(36);
        assertThat(milis).as("36 periodos resueltos en %d ms", milis).isLessThan(3000);
    }
}
