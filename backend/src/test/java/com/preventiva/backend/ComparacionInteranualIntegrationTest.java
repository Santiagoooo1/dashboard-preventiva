package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CeldaComparacionDto;
import com.preventiva.backend.dto.ComparacionInteranualRequestDto;
import com.preventiva.backend.dto.ComparacionInteranualResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.SerieAnualDto;
import com.preventiva.backend.dto.ValorCategoriaDto;
import com.preventiva.backend.enums.EstadoResultadoMetrica;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoComparacionInteranual;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.ComparacionInteranualService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Fase 6.9O — comparación de un mismo concepto clínico entre años.
 *
 * <p>Lo que más se protege aquí es el total de cada año. En vigilancia esa cifra
 * se lee como «la tasa del año», y promediar las tasas mensuales pondera igual
 * un mes de 100 intervenciones y uno de 50: con 1/100 en enero y 1/50 en
 * febrero, la media da 1,5 % y la tasa real es 1,33 %.
 *
 * <p>Lo segundo, que dos datasets solo se comparen cuando de verdad son
 * comparables. Una columna vacía en 2024 se lee como «ese año no hubo casos»,
 * cuando lo que pasaba es que ese año no se recogía el dato.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ComparacionInteranualIntegrationTest {

    private static final String PREFIJO = "TEST_69O_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private ComparacionInteranualService comparacionService;

    private static final String C_ILQ = "infeccionLocalizacionQuirurgica";
    private static final String C_LOCALIZACION = "localizacionInfeccion";
    private static final String C_DURACION = "duracionCirugia";
    private static final String C_SERVICIO = "servicio";

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Long crearDataset(String nombre) {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre(nombre);
        return datasetClinicoService.crear(r).getId();
    }

    private void crearCampo(Long datasetId, String codigo, String etiqueta, TipoDatoExcel tipo, boolean comun) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(etiqueta);
        r.setTipoDato(tipo);
        r.setEsComun(comun);
        r.setObligatorio(false);
        campoClinicoService.crear(datasetId, r);
    }

    /** Dataset con los campos habituales; la etiqueta del concepto varía a propósito. */
    private Long datasetIlq(String nombre, String etiquetaLocalizacion) {
        Long id = crearDataset(nombre);
        crearCampo(id, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        crearCampo(id, "fechaEvento", "Fecha", TipoDatoExcel.FECHA, true);
        crearCampo(id, C_SERVICIO, "Servicio", TipoDatoExcel.TEXTO, true);
        crearCampo(id, C_ILQ, "ILQ", TipoDatoExcel.BOOLEANO, false);
        crearCampo(id, C_LOCALIZACION, etiquetaLocalizacion, TipoDatoExcel.TEXTO, false);
        crearCampo(id, C_DURACION, "Duración", TipoDatoExcel.ENTERO, false);
        return id;
    }

    private void registro(Long datasetId, LocalDate fecha, String ilq,
                          String localizacion, Integer duracion, String servicio) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo("P" + contador.incrementAndGet());
        r.setFechaEvento(fecha);
        r.setServicio(servicio);
        Map<String, Object> d = new HashMap<>();
        if (ilq != null) d.put(C_ILQ, ilq);
        if (localizacion != null) d.put(C_LOCALIZACION, localizacion);
        if (duracion != null) d.put(C_DURACION, duracion);
        r.setDatosDinamicos(d);
        registroClinicoGenericoService.crear(r);
    }

    /** Carga un mes con `total` intervenciones de las que `casos` tienen ILQ. */
    private void mes(Long datasetId, int anio, int mesNumero, int total, int casos) {
        for (int i = 0; i < total; i++) {
            registro(datasetId, LocalDate.of(anio, mesNumero, 1 + i % 27),
                    i < casos ? "SI" : "NO", null, null, "TRAUMA");
        }
    }

    private ComparacionInteranualRequestDto peticion(
            List<Long> datasets, String codigo, TipoComparacionInteranual tipo) {
        ComparacionInteranualRequestDto r = new ComparacionInteranualRequestDto();
        r.setDatasetIds(datasets);
        r.setCodigoCanonico(codigo);
        r.setTipoComparacion(tipo);
        r.setGranularidad(Granularidad.MES);
        return r;
    }

    private CeldaComparacionDto celdaDe(SerieAnualDto serie, String periodo) {
        return serie.getCeldas().stream()
                .filter(c -> periodo.equals(c.getPeriodo())).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------
    // §33: el caso ILQ interanual obligatorio
    // ------------------------------------------------------------------

    /**
     * §33 e I: el escenario del enunciado, con tres datasets de un año cada uno.
     *
     * <p>El total de 2026 es 2/150 = 1,33 %, no la media de 1 % y 2 % = 1,5 %.
     */
    @Test
    void comparacionIlqEntreTresAnios_conTotalesAcumuladosYNoPromediados() {
        Long d2024 = datasetIlq("ILQ 2024", "Localización de la infección");
        mes(d2024, 2024, 1, 100, 2);
        mes(d2024, 2024, 2, 100, 3);

        Long d2025 = datasetIlq("ILQ 2025", "Sitio de la infección");
        mes(d2025, 2025, 1, 100, 4);
        mes(d2025, 2025, 2, 100, 2);

        Long d2026 = datasetIlq("ILQ 2026", "Localización ILQ");
        mes(d2026, 2026, 1, 100, 1);
        mes(d2026, 2026, 2, 50, 1);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(d2024, d2025, d2026), C_ILQ, TipoComparacionInteranual.TASA));

        assertThat(r.isComparable()).isTrue();
        assertThat(r.getSeries()).extracting(SerieAnualDto::getAnio).containsExactly(2024, 2025, 2026);

        // Enero: 2 %, 4 %, 1 %
        assertThat(celdaDe(r.getSeries().get(0), "01").getValor()).isCloseTo(2.0, within(0.01));
        assertThat(celdaDe(r.getSeries().get(1), "01").getValor()).isCloseTo(4.0, within(0.01));
        assertThat(celdaDe(r.getSeries().get(2), "01").getValor()).isCloseTo(1.0, within(0.01));
        // Febrero: 3 %, 2 %, 2 %
        assertThat(celdaDe(r.getSeries().get(0), "02").getValor()).isCloseTo(3.0, within(0.01));
        assertThat(celdaDe(r.getSeries().get(1), "02").getValor()).isCloseTo(2.0, within(0.01));
        assertThat(celdaDe(r.getSeries().get(2), "02").getValor()).isCloseTo(2.0, within(0.01));

        // Totales acumulados
        assertThat(r.getSeries().get(0).getTotal().getValor()).isCloseTo(2.5, within(0.01));
        assertThat(r.getSeries().get(1).getTotal().getValor()).isCloseTo(3.0, within(0.01));

        CeldaComparacionDto total2026 = r.getSeries().get(2).getTotal();
        assertThat(total2026.getNumerador()).isEqualTo(2);
        assertThat(total2026.getDenominador()).isEqualTo(150);
        assertThat(total2026.getValor())
                .as("2/150 acumulado, no la media de 1 %% y 2 %% = 1,5 %%")
                .isCloseTo(1.33, within(0.01));
        assertThat(total2026.getValor()).isNotEqualTo(1.5);
    }

    /** A y K: un solo dataset con tres años se divide en tres series. */
    @Test
    void unDatasetConTresAnios_seDivideEnTresSeries() {
        Long id = datasetIlq("ILQ histórico", "Localización de la infección");
        mes(id, 2024, 1, 100, 2);
        mes(id, 2025, 1, 100, 4);
        mes(id, 2026, 1, 100, 1);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_ILQ, TipoComparacionInteranual.TASA));

        assertThat(r.getSeries()).hasSize(3);
        assertThat(r.getSeries()).extracting(SerieAnualDto::getAnio).containsExactly(2024, 2025, 2026);
        assertThat(r.getSeries()).extracting(SerieAnualDto::getEtiqueta).containsExactly("2024", "2025", "2026");
        assertThat(r.getAdvertencias()).extracting(a -> a.getCodigo()).contains("DATASET_MULTIANUAL");
    }

    /**
     * C: los tres datasets escriben la columna de otra forma («Localización de
     * la infección», «Sitio de la infección», «Localización ILQ») y aun así son
     * comparables, porque lo que se une es el código canónico.
     */
    @Test
    void cabecerasDistintas_songComparablesPorCodigoCanonico() {
        Long a = datasetIlq("ILQ 2024", "LOCALIZACIÓN DE LA INFECCIÓN");
        Long b = datasetIlq("ILQ 2025", "SITIO DE LA INFECCIÓN");
        Long c = datasetIlq("ILQ 2026", "LOCALIZACIÓN ILQ");
        registro(a, LocalDate.of(2024, 1, 5), "SI", "PROFUNDA", null, "TRAUMA");
        registro(b, LocalDate.of(2025, 1, 5), "SI", "PROFUNDA", null, "TRAUMA");
        registro(c, LocalDate.of(2026, 1, 5), "SI", "PROFUNDA", null, "TRAUMA");

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(a, b, c), C_LOCALIZACION, TipoComparacionInteranual.DISTRIBUCION));

        assertThat(r.isComparable()).isTrue();
        assertThat(r.getSeries()).hasSize(3);
        // La etiqueta legible sale de los campos, aunque cada año la escriba distinto.
        assertThat(r.getEtiquetaConcepto()).isNotBlank();
    }

    // ------------------------------------------------------------------
    // D–E: incompatibilidades
    // ------------------------------------------------------------------

    /** D: si un año no tiene el concepto, no se compara y se dice cuál falta. */
    @Test
    void campoAusenteEnUnAnio_noProduceComparacion() {
        Long completo = datasetIlq("ILQ 2025", "Localización de la infección");
        registro(completo, LocalDate.of(2025, 1, 5), "SI", "PROFUNDA", null, "TRAUMA");

        Long incompleto = crearDataset("ILQ 2024");
        crearCampo(incompleto, "pacienteCodigo", "HC", TipoDatoExcel.TEXTO, true);
        crearCampo(incompleto, "fechaEvento", "Fecha", TipoDatoExcel.FECHA, true);
        crearCampo(incompleto, C_ILQ, "ILQ", TipoDatoExcel.BOOLEANO, false);
        registro(incompleto, LocalDate.of(2024, 1, 5), "NO", null, null, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(incompleto, completo), C_LOCALIZACION, TipoComparacionInteranual.DISTRIBUCION));

        assertThat(r.isComparable()).isFalse();
        assertThat(r.getSeries()).isEmpty();
        assertThat(r.getMotivoNoComparable())
                .contains("ILQ 2024")
                .contains(C_LOCALIZACION);
    }

    /** E: el mismo código con tipos distintos no es comparable. */
    @Test
    void tiposIncompatibles_noProducenComparacion() {
        Long texto = datasetIlq("ILQ 2025", "Localización");
        registro(texto, LocalDate.of(2025, 1, 5), "SI", "PROFUNDA", null, "TRAUMA");

        Long booleano = crearDataset("ILQ 2024");
        crearCampo(booleano, "fechaEvento", "Fecha", TipoDatoExcel.FECHA, true);
        crearCampo(booleano, C_ILQ, "ILQ", TipoDatoExcel.BOOLEANO, false);
        // Aquí la localización se importó mal, como Sí/No.
        crearCampo(booleano, C_LOCALIZACION, "Localización", TipoDatoExcel.BOOLEANO, false);
        registro(booleano, LocalDate.of(2024, 1, 5), "SI", null, null, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(booleano, texto), C_LOCALIZACION, TipoComparacionInteranual.DISTRIBUCION));

        assertThat(r.isComparable()).isFalse();
        assertThat(r.getMotivoNoComparable()).containsIgnoringCase("incompatibles");
    }

    /** Sin fecha de evento no hay eje temporal que compartir. */
    @Test
    void datasetSinFechaEvento_noEsComparable() {
        Long conFecha = datasetIlq("ILQ 2025", "Localización");
        registro(conFecha, LocalDate.of(2025, 1, 5), "SI", null, null, null);

        Long sinFecha = crearDataset("ILQ 2024");
        crearCampo(sinFecha, C_ILQ, "ILQ", TipoDatoExcel.BOOLEANO, false);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(sinFecha, conFecha), C_ILQ, TipoComparacionInteranual.TASA));

        assertThat(r.isComparable()).isFalse();
        assertThat(r.getMotivoNoComparable()).contains("ILQ 2024");
    }

    /** Un concepto de texto no admite una comparación de tasa. */
    @Test
    void tipoDeComparacionQueElCampoNoAdmite_seExplica() {
        Long id = datasetIlq("ILQ 2025", "Localización");
        registro(id, LocalDate.of(2025, 1, 5), "SI", "PROFUNDA", null, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_LOCALIZACION, TipoComparacionInteranual.TASA));

        assertThat(r.isComparable()).isFalse();
        assertThat(r.getMotivoNoComparable()).contains("TEXTO").contains("TASA");
    }

    // ------------------------------------------------------------------
    // F–G: categorías entre años
    // ------------------------------------------------------------------

    /**
     * F y G: una categoría que solo aparece en 2025 se conserva en la unión, y
     * en 2024 vale 0 —el concepto existía, simplemente no hubo casos—. No se
     * elimina la columna ni se confunde con «no comparable».
     */
    @Test
    void categoriaQueSoloExisteEnUnAnio_seConservaEnLaUnionConCeroEnElOtro() {
        Long d2024 = datasetIlq("ILQ 2024", "Localización");
        registro(d2024, LocalDate.of(2024, 1, 5), "SI", "SUPERFICIAL", null, null);
        registro(d2024, LocalDate.of(2024, 1, 6), "SI", "PROFUNDA", null, null);

        Long d2025 = datasetIlq("ILQ 2025", "Localización");
        registro(d2025, LocalDate.of(2025, 1, 5), "SI", "SUPERFICIAL", null, null);
        registro(d2025, LocalDate.of(2025, 1, 6), "SI", "ORGANO_ESPACIO", null, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(d2024, d2025), C_LOCALIZACION, TipoComparacionInteranual.DISTRIBUCION));

        assertThat(r.getCategorias())
                .as("la unión, no la intersección")
                .containsExactlyInAnyOrder("SUPERFICIAL", "PROFUNDA", "ORGANO_ESPACIO");

        // 2024 no tuvo ORGANO_ESPACIO: es un cero real, y aparece como tal.
        CeldaComparacionDto enero2024 = celdaDe(r.getSeries().get(0), "01");
        Map<String, Double> valores2024 = enero2024.getCategorias().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ValorCategoriaDto::getCategoria, ValorCategoriaDto::getValor));
        assertThat(valores2024).containsEntry("ORGANO_ESPACIO", 0.0);
        assertThat(valores2024).containsEntry("SUPERFICIAL", 1.0);
        assertThat(valores2024).containsEntry("PROFUNDA", 1.0);

        // Y todas las series enseñan todas las categorías, para poder alinearlas.
        for (SerieAnualDto serie : r.getSeries()) {
            for (CeldaComparacionDto celda : serie.getCeldas()) {
                assertThat(celda.getCategorias()).hasSize(3);
            }
        }
    }

    /** El concepto ausente NO se representa como cero: la comparación se rechaza. */
    @Test
    void ceroCasos_seDistingueDeConceptoInexistente() {
        Long conCasos = datasetIlq("ILQ 2025", "Localización");
        registro(conCasos, LocalDate.of(2025, 1, 5), "SI", "PROFUNDA", null, null);
        Long sinCasos = datasetIlq("ILQ 2024", "Localización");
        registro(sinCasos, LocalDate.of(2024, 1, 5), "NO", null, null, null);

        // Ambos tienen el campo: comparables, y 2024 sale a cero.
        ComparacionInteranualResponseDto conCampo = comparacionService.comparar(
                peticion(List.of(sinCasos, conCasos), C_LOCALIZACION, TipoComparacionInteranual.DISTRIBUCION));
        assertThat(conCampo.isComparable()).isTrue();
        assertThat(conCampo.getSeries().get(0).getTotal().getDenominador()).isZero();

        // Sin el campo: NO comparable, no un cero.
        Long sinCampo = crearDataset("ILQ 2023");
        crearCampo(sinCampo, "fechaEvento", "Fecha", TipoDatoExcel.FECHA, true);
        crearCampo(sinCampo, C_ILQ, "ILQ", TipoDatoExcel.BOOLEANO, false);
        registro(sinCampo, LocalDate.of(2023, 1, 5), "NO", null, null, null);

        ComparacionInteranualResponseDto sinElCampo = comparacionService.comparar(
                peticion(List.of(sinCampo, conCasos), C_LOCALIZACION, TipoComparacionInteranual.DISTRIBUCION));
        assertThat(sinElCampo.isComparable()).isFalse();
    }

    // ------------------------------------------------------------------
    // H–J: eje, totales y variación
    // ------------------------------------------------------------------

    /** H: el eje va de enero a diciembre, igual para todos los años. */
    @Test
    void elEje_vaDeEneroADiciembreYEsElMismoParaTodosLosAnios() {
        Long id = datasetIlq("ILQ", "Localización");
        mes(id, 2025, 11, 10, 1);
        mes(id, 2025, 2, 10, 0);
        mes(id, 2026, 3, 10, 0);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_ILQ, TipoComparacionInteranual.TASA));

        assertThat(r.getPeriodos()).containsExactly(
                "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12");
        for (SerieAnualDto serie : r.getSeries()) {
            assertThat(serie.getCeldas()).extracting(CeldaComparacionDto::getPeriodo)
                    .isEqualTo(r.getPeriodos());
        }
    }

    /** J: la variación de un porcentaje va en puntos porcentuales. */
    @Test
    void laVariacionDeUnPorcentaje_vaEnPuntosPorcentuales() {
        Long d2025 = datasetIlq("ILQ 2025", "Localización");
        mes(d2025, 2025, 1, 100, 31);   // 31 %
        Long d2026 = datasetIlq("ILQ 2026", "Localización");
        mes(d2026, 2026, 1, 100, 18);   // 18 %

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(d2025, d2026), C_ILQ, TipoComparacionInteranual.TASA));

        SerieAnualDto serie2026 = r.getSeries().get(1);
        assertThat(serie2026.getUnidadVariacion()).isEqualTo("pp");

        CeldaComparacionDto variacionEnero = serie2026.getVariacion().stream()
                .filter(c -> "01".equals(c.getPeriodo())).findFirst().orElseThrow();
        assertThat(variacionEnero.getValor())
                .as("18 - 31 = -13 pp; NO -41,9 %% relativo")
                .isCloseTo(-13.0, within(0.01));
        assertThat(variacionEnero.getValor()).isNotEqualTo(-41.94);

        assertThat(serie2026.getVariacionTotal().getValor()).isCloseTo(-13.0, within(0.01));
        // La primera serie no tiene con qué compararse.
        assertThat(r.getSeries().get(0).getVariacion()).isNull();
    }

    /** Sin base evaluable no hay variación: cero diría «no ha cambiado». */
    @Test
    void sinBaseEvaluable_laVariacionEsNulaYNoCero() {
        Long d2025 = datasetIlq("ILQ 2025", "Localización");
        mes(d2025, 2025, 1, 10, 1);
        Long d2026 = datasetIlq("ILQ 2026", "Localización");
        // 2026 solo tiene registros sin el dato documentado.
        registro(d2026, LocalDate.of(2026, 1, 5), null, null, null, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(d2025, d2026), C_ILQ, TipoComparacionInteranual.TASA));

        CeldaComparacionDto enero2026 = celdaDe(r.getSeries().get(1), "01");
        assertThat(enero2026.getValor()).isNull();
        assertThat(enero2026.getEstado()).isEqualTo(EstadoResultadoMetrica.SIN_BASE_EVALUABLE.name());

        CeldaComparacionDto variacion = r.getSeries().get(1).getVariacion().stream()
                .filter(c -> "01".equals(c.getPeriodo())).findFirst().orElseThrow();
        assertThat(variacion.getValor()).isNull();
    }

    // ------------------------------------------------------------------
    // 6.9P.1 — recuento de casos
    // ------------------------------------------------------------------

    /**
     * A: el número absoluto de casos, que no es lo mismo que la tasa. Un año con
     * menos intervenciones puede tener menos casos y peor tasa, y el servicio
     * necesita ver las dos cifras.
     */
    @Test
    void recuento_devuelveLosCasosPositivosDeCadaAnio() {
        Long d2024 = datasetIlq("ILQ 2024", "Localización");
        mes(d2024, 2024, 1, 100, 8);
        Long d2025 = datasetIlq("ILQ 2025", "Localización");
        mes(d2025, 2025, 1, 100, 9);
        Long d2026 = datasetIlq("ILQ 2026", "Localización");
        mes(d2026, 2026, 1, 100, 6);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(d2024, d2025, d2026), C_ILQ, TipoComparacionInteranual.RECUENTO));

        assertThat(r.isComparable()).isTrue();
        assertThat(r.getSeries()).extracting(s -> s.getTotal().getValor())
                .containsExactly(8.0, 9.0, 6.0);
        assertThat(celdaDe(r.getSeries().get(0), "01").getValor()).isEqualTo(8.0);
    }

    /** B: ni un false ni un NULL son un caso. */
    @Test
    void recuento_noCuentaLosFalseNiLosNulos() {
        Long id = datasetIlq("ILQ 2026", "Localización");
        registro(id, LocalDate.of(2026, 1, 5), "SI", null, null, null);
        registro(id, LocalDate.of(2026, 1, 6), "SI", null, null, null);
        registro(id, LocalDate.of(2026, 1, 7), "NO", null, null, null);
        registro(id, LocalDate.of(2026, 1, 8), null, null, null, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_ILQ, TipoComparacionInteranual.RECUENTO));

        assertThat(r.getSeries().get(0).getTotal().getValor())
                .as("solo los 2 positivos de 4 registros")
                .isEqualTo(2.0);
    }

    /** C y D: la variación de un recuento es absoluta, no en puntos porcentuales. */
    @Test
    void recuento_laVariacionEsAbsoluta() {
        Long d2025 = datasetIlq("ILQ 2025", "Localización");
        mes(d2025, 2025, 1, 100, 9);
        Long d2026 = datasetIlq("ILQ 2026", "Localización");
        mes(d2026, 2026, 1, 100, 6);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(d2025, d2026), C_ILQ, TipoComparacionInteranual.RECUENTO));

        SerieAnualDto serie2026 = r.getSeries().get(1);
        assertThat(serie2026.getUnidadVariacion()).isEqualTo("absoluta");
        assertThat(serie2026.getVariacionTotal().getValor()).isEqualTo(-3.0);
    }

    /** Un recuento no es una fracción: no viaja con numerador ni denominador. */
    @Test
    void recuento_noSePresentaComoUnaFraccion() {
        Long id = datasetIlq("ILQ 2026", "Localización");
        mes(id, 2026, 1, 100, 6);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_ILQ, TipoComparacionInteranual.RECUENTO));

        CeldaComparacionDto total = r.getSeries().get(0).getTotal();
        assertThat(total.getValor()).isEqualTo(6.0);
        assertThat(total.getNumerador()).isNull();
        assertThat(total.getDenominador()).isNull();
    }

    /** G: un dataset multianual también admite el recuento. */
    @Test
    void recuento_funcionaEnUnDatasetMultianual() {
        Long id = datasetIlq("ILQ histórico", "Localización");
        mes(id, 2024, 1, 100, 8);
        mes(id, 2025, 1, 100, 9);
        mes(id, 2026, 1, 100, 6);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_ILQ, TipoComparacionInteranual.RECUENTO));

        assertThat(r.getSeries()).hasSize(3);
        assertThat(r.getSeries()).extracting(s -> s.getTotal().getValor())
                .containsExactly(8.0, 9.0, 6.0);
    }

    /** El recuento respeta los filtros igual que el resto de comparaciones. */
    @Test
    void recuento_respetaLosFiltros() {
        Long id = datasetIlq("ILQ 2026", "Localización");
        registro(id, LocalDate.of(2026, 1, 5), "SI", null, null, "TRAUMA");
        registro(id, LocalDate.of(2026, 1, 6), "SI", null, null, "CARDIO");
        registro(id, LocalDate.of(2026, 1, 7), "SI", null, null, "TRAUMA");

        ComparacionInteranualRequestDto p = peticion(List.of(id), C_ILQ, TipoComparacionInteranual.RECUENTO);
        FiltroMetricaDto filtro = new FiltroMetricaDto();
        filtro.setCampo(C_SERVICIO);
        filtro.setOperador(OperadorFiltro.EQ);
        filtro.setValor("TRAUMA");
        p.setFiltros(List.of(filtro));

        assertThat(comparacionService.comparar(p).getSeries().get(0).getTotal().getValor()).isEqualTo(2.0);
    }

    /** Un texto no admite recuento de positivos: no hay «true» que contar. */
    @Test
    void recuento_soloAdmiteBooleanos() {
        Long id = datasetIlq("ILQ 2026", "Localización");
        registro(id, LocalDate.of(2026, 1, 5), "SI", "PROFUNDA", null, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_LOCALIZACION, TipoComparacionInteranual.RECUENTO));

        assertThat(r.isComparable()).isFalse();
        assertThat(r.getMotivoNoComparable()).contains("RECUENTO");
    }

    /**
     * E y F: añadir el recuento no ha movido nada. Los mismos datos siguen
     * dando la misma tasa y la misma distribución.
     */
    @Test
    void anadirElRecuento_noHaCambiadoTasaNiDistribucion() {
        Long d2025 = datasetIlq("ILQ 2025", "Localización");
        mes(d2025, 2025, 1, 100, 9);
        registro(d2025, LocalDate.of(2025, 2, 5), "SI", "PROFUNDA", null, null);
        Long d2026 = datasetIlq("ILQ 2026", "Localización");
        mes(d2026, 2026, 1, 100, 6);
        registro(d2026, LocalDate.of(2026, 2, 5), "SI", "SUPERFICIAL", null, null);

        ComparacionInteranualResponseDto tasa = comparacionService.comparar(
                peticion(List.of(d2025, d2026), C_ILQ, TipoComparacionInteranual.TASA));
        assertThat(tasa.getSeries().get(0).getTotal().getNumerador()).isEqualTo(10);
        assertThat(tasa.getSeries().get(0).getTotal().getDenominador()).isEqualTo(101);
        assertThat(tasa.getSeries().get(0).getUnidadVariacion()).isEqualTo("pp");

        ComparacionInteranualResponseDto distribucion = comparacionService.comparar(
                peticion(List.of(d2025, d2026), C_LOCALIZACION, TipoComparacionInteranual.DISTRIBUCION));
        assertThat(distribucion.isComparable()).isTrue();
        assertThat(distribucion.getCategorias()).containsExactlyInAnyOrder("PROFUNDA", "SUPERFICIAL");
    }

    // ------------------------------------------------------------------
    // §24 y §26: denominadores visibles y resumen anual
    // ------------------------------------------------------------------

    /** §24: la celda conserva numerador y denominador, no solo el porcentaje. */
    @Test
    void cadaCelda_conservaNumeradorYDenominador() {
        Long id = datasetIlq("ILQ 2026", "Localización");
        mes(id, 2026, 1, 222, 4);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(id), C_ILQ, TipoComparacionInteranual.TASA));

        CeldaComparacionDto enero = celdaDe(r.getSeries().get(0), "01");
        assertThat(enero.getNumerador()).isEqualTo(4);
        assertThat(enero.getDenominador()).isEqualTo(222);
        assertThat(enero.getValor()).isCloseTo(1.8, within(0.01));
    }

    /** §26: el resumen numérico por año trae N, media, mínimo y máximo. */
    @Test
    void resumenNumericoPorAnio() {
        Long d2025 = datasetIlq("Cirugías 2025", "Localización");
        registro(d2025, LocalDate.of(2025, 1, 5), null, null, 60, null);
        registro(d2025, LocalDate.of(2025, 1, 6), null, null, 120, null);
        Long d2026 = datasetIlq("Cirugías 2026", "Localización");
        registro(d2026, LocalDate.of(2026, 1, 5), null, null, 90, null);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(d2025, d2026), C_DURACION, TipoComparacionInteranual.RESUMEN_NUMERICO));

        assertThat(r.isComparable()).isTrue();
        CeldaComparacionDto total2025 = r.getSeries().get(0).getTotal();
        assertThat(total2025.getMedia()).isCloseTo(90.0, within(0.01));
        assertThat(total2025.getMinimo()).isEqualTo(60.0);
        assertThat(total2025.getMaximo()).isEqualTo(120.0);
        assertThat(total2025.getDenominador()).as("N").isEqualTo(2);
        assertThat(r.getSeries().get(1).getUnidadVariacion()).isEqualTo("absoluta");
    }

    // ------------------------------------------------------------------
    // L–N: solapamiento y filtros
    // ------------------------------------------------------------------

    /** L: dos datasets que cubren el mismo año se avisan, pero no se deduplican. */
    @Test
    void periodosSolapados_producenAdvertenciaSinDeduplicar() {
        Long completo = datasetIlq("ILQ 2025 completo", "Localización");
        mes(completo, 2025, 1, 10, 1);
        mes(completo, 2025, 7, 10, 0);

        Long parcial = datasetIlq("ILQ 2025 primer semestre", "Localización");
        mes(parcial, 2025, 1, 10, 1);

        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(List.of(completo, parcial), C_ILQ, TipoComparacionInteranual.TASA));

        assertThat(r.isComparable()).isTrue();
        assertThat(r.getAdvertencias()).extracting(a -> a.getCodigo()).contains("PERIODOS_SOLAPADOS");
        assertThat(r.getAdvertencias()).anySatisfy(a ->
                assertThat(a.getMensaje()).contains("2025").containsIgnoringCase("duplicados"));
        // Y no se ha fusionado nada: siguen siendo dos series.
        assertThat(r.getSeries()).hasSize(2);
    }

    /** M: un filtro que existe en todos los datasets se aplica en todos. */
    @Test
    void filtroPresenteEnTodos_seAplicaATodasLasSeries() {
        Long d2025 = datasetIlq("ILQ 2025", "Localización");
        registro(d2025, LocalDate.of(2025, 1, 5), "SI", null, null, "TRAUMA");
        registro(d2025, LocalDate.of(2025, 1, 6), "NO", null, null, "TRAUMA");
        registro(d2025, LocalDate.of(2025, 1, 7), "SI", null, null, "CARDIO");
        Long d2026 = datasetIlq("ILQ 2026", "Localización");
        registro(d2026, LocalDate.of(2026, 1, 5), "NO", null, null, "TRAUMA");
        registro(d2026, LocalDate.of(2026, 1, 6), "SI", null, null, "CARDIO");

        ComparacionInteranualRequestDto peticion =
                peticion(List.of(d2025, d2026), C_ILQ, TipoComparacionInteranual.TASA);
        FiltroMetricaDto filtro = new FiltroMetricaDto();
        filtro.setCampo(C_SERVICIO);
        filtro.setOperador(OperadorFiltro.EQ);
        filtro.setValor("TRAUMA");
        peticion.setFiltros(List.of(filtro));

        ComparacionInteranualResponseDto r = comparacionService.comparar(peticion);

        assertThat(r.isComparable()).isTrue();
        assertThat(r.getSeries().get(0).getTotal().getDenominador()).as("solo TRAUMA en 2025").isEqualTo(2);
        assertThat(r.getSeries().get(1).getTotal().getDenominador()).as("solo TRAUMA en 2026").isEqualTo(1);
    }

    /** N: un filtro que falta en un dataset hace la comparación no equivalente. */
    @Test
    void filtroAusenteEnUnDataset_noProduceComparacionEnganosa() {
        Long conServicio = datasetIlq("ILQ 2025", "Localización");
        registro(conServicio, LocalDate.of(2025, 1, 5), "SI", null, null, "TRAUMA");

        Long sinServicio = crearDataset("ILQ 2024");
        crearCampo(sinServicio, "fechaEvento", "Fecha", TipoDatoExcel.FECHA, true);
        crearCampo(sinServicio, C_ILQ, "ILQ", TipoDatoExcel.BOOLEANO, false);
        registro(sinServicio, LocalDate.of(2024, 1, 5), "NO", null, null, null);

        ComparacionInteranualRequestDto peticion =
                peticion(List.of(sinServicio, conServicio), C_ILQ, TipoComparacionInteranual.TASA);
        FiltroMetricaDto filtro = new FiltroMetricaDto();
        filtro.setCampo(C_SERVICIO);
        filtro.setOperador(OperadorFiltro.EQ);
        filtro.setValor("TRAUMA");
        peticion.setFiltros(List.of(filtro));

        ComparacionInteranualResponseDto r = comparacionService.comparar(peticion);

        assertThat(r.isComparable()).isFalse();
        assertThat(r.getMotivoNoComparable()).contains(C_SERVICIO).contains("ILQ 2024");
    }

    // ------------------------------------------------------------------
    // O: rendimiento
    // ------------------------------------------------------------------

    /**
     * O: la comparación no puede hacer una consulta por año, mes o categoría. Se
     * comprueba de forma indirecta pero real: tres datasets con tres años y doce
     * meses son 108 celdas, y deben salir en una pasada por dataset.
     */
    @Test
    void tresDatasetsConVariosAnios_noDisparanElTiempoDeCalculo() {
        List<Long> datasets = new java.util.ArrayList<>();
        for (int d = 0; d < 3; d++) {
            Long id = datasetIlq("ILQ bloque " + d, "Localización");
            for (int anio = 2024; anio <= 2026; anio++) {
                for (int m = 1; m <= 12; m++) {
                    registro(id, LocalDate.of(anio, m, 10), m % 5 == 0 ? "SI" : "NO", null, null, "TRAUMA");
                }
            }
            datasets.add(id);
        }

        long inicio = System.nanoTime();
        ComparacionInteranualResponseDto r = comparacionService.comparar(
                peticion(datasets, C_ILQ, TipoComparacionInteranual.TASA));
        long milis = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(r.getSeries()).hasSize(9);
        assertThat(milis).as("9 series × 12 periodos resueltas en %d ms", milis).isLessThan(3000);
    }

    /** Comparar es una consulta: no crea ni cambia nada. */
    @Test
    void compararNoModificaNada() {
        Long id = datasetIlq("ILQ 2026", "Localización");
        mes(id, 2026, 1, 10, 1);

        comparacionService.comparar(peticion(List.of(id), C_ILQ, TipoComparacionInteranual.TASA));
        comparacionService.comparar(peticion(List.of(id), C_ILQ, TipoComparacionInteranual.TASA));

        assertThat(campoClinicoService.listarPorDataset(id)).hasSize(6);
    }
}
