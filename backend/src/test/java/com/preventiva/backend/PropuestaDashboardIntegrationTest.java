package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoRecomendadoDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.ConfiguracionWidgetDto;
import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardPanelResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.PanelMetricaConfiguracionWidgetRequestDto;
import com.preventiva.backend.dto.PanelMetricaRequestDto;
import com.preventiva.backend.dto.PanelMetricaResponseDto;
import com.preventiva.backend.dto.PropuestaDashboardResponseDto;
import com.preventiva.backend.dto.PropuestaWidgetDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.service.impl.PropuestaDashboardServiceImpl;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DashboardPanelService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.PanelClinicoService;
import com.preventiva.backend.service.interfaces.PanelMetricaService;
import com.preventiva.backend.service.interfaces.PropuestaDashboardService;
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

/**
 * Fase 6.9J.2 — composición de la propuesta de dashboard.
 *
 * <p>Cubre las tres cosas que decide el servicio: qué columnas merecen widget,
 * qué representación les corresponde y en qué orden aparecen. Y que el
 * dashboard resultante quede equilibrado, no saturado.
 *
 * <p>Incluye además la cobertura de edición y persistencia de widgets que se
 * perdió al no llegar su fichero al commit de la entrega piloto.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PropuestaDashboardIntegrationTest {

    private static final String PREFIJO = "TEST_69J2_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private MetricaClinicaService metricaClinicaService;
    @Autowired private PanelClinicoService panelClinicoService;
    @Autowired private PanelMetricaService panelMetricaService;
    @Autowired private DashboardPanelService dashboardPanelService;
    @Autowired private PropuestaDashboardService propuestaDashboardService;

    // ==================================================================
    // Utilidades
    // ==================================================================

    private String codigoUnico(String s) {
        return PREFIJO + s + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(codigoUnico("DS"));
        r.setNombre("Test 6.9J.2");
        return datasetClinicoService.crear(r).getId();
    }

    /** Crea un campo. La prioridad es explícita: nunca se deduce de los flags. */
    private void campo(
            Long datasetId, String codigo, TipoDatoExcel tipo, PrioridadDashboardCampo prioridad) {
        campo(datasetId, codigo, tipo, prioridad, false, false);
    }

    private void campo(
            Long datasetId, String codigo, TipoDatoExcel tipo, PrioridadDashboardCampo prioridad,
            boolean comun, boolean obligatorio) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(codigo);
        r.setTipoDato(tipo);
        r.setEsComun(comun);
        r.setObligatorio(obligatorio);
        r.setPrioridadDashboard(prioridad);
        campoClinicoService.crear(datasetId, r);
    }

    private void registro(Long datasetId, String paciente, LocalDate fecha, Map<String, Object> dinamicos) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setFechaEvento(fecha);
        r.setDatosDinamicos(dinamicos);
        registroClinicoGenericoService.crear(r);
    }

    private List<String> codigos(PropuestaDashboardResponseDto r) {
        return r.getPropuestas().stream().map(PropuestaWidgetDto::getCodigoMetrica).toList();
    }

    private PropuestaWidgetDto propuestaDe(PropuestaDashboardResponseDto r, String campoOrigen) {
        return r.getPropuestas().stream()
                .filter(p -> campoOrigen.equals(p.getCampoOrigen()))
                .findFirst().orElse(null);
    }

    /** Esqueleto mínimo para que un dataset tenga registros que perfilar. */
    private Long datasetBase() {
        Long id = crearDataset();
        campo(id, "pacienteCodigo", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, true, false);
        campo(id, "fechaEvento", TipoDatoExcel.FECHA, PrioridadDashboardCampo.NORMAL, true, false);
        return id;
    }

    private void poblar(Long datasetId, int filas, Map<String, java.util.function.IntFunction<Object>> valores) {
        for (int i = 1; i <= filas; i++) {
            Map<String, Object> d = new HashMap<>();
            for (Map.Entry<String, java.util.function.IntFunction<Object>> e : valores.entrySet()) {
                d.put(e.getKey(), e.getValue().apply(i));
            }
            registro(datasetId, "P" + (i % 7), LocalDate.of(2026, (i % 12) + 1, 5), d);
        }
    }

    // ==================================================================
    // A y B — la relevancia declarada ordena
    // ==================================================================

    @Test
    void A_fundamentalGanaAImportanteEnIgualdadAnalitica() {
        Long id = datasetBase();
        // Dos categóricas idénticas salvo por la prioridad.
        campo(id, "importante", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.IMPORTANTE);
        campo(id, "fundamental", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.FUNDAMENTAL);
        poblar(id, 12, Map.of(
                "importante", i -> "V" + (i % 3),
                "fundamental", i -> "W" + (i % 3)));

        List<String> orden = codigos(propuestaDashboardService.proponer(id));

        assertThat(orden.indexOf("distribucion_fundamental"))
                .isLessThan(orden.indexOf("distribucion_importante"));
    }

    @Test
    void B_importanteGanaANormalEnIgualdadAnalitica() {
        Long id = datasetBase();
        campo(id, "normal", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);
        campo(id, "importante", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.IMPORTANTE);
        poblar(id, 12, Map.of(
                "normal", i -> "V" + (i % 3),
                "importante", i -> "W" + (i % 3)));

        List<String> orden = codigos(propuestaDashboardService.proponer(id));

        assertThat(orden.indexOf("distribucion_importante"))
                .isLessThan(orden.indexOf("distribucion_normal"));
    }

    @Test
    void laAptitudCompensaUnEscalonDeRelevanciaPeroNoDos() {
        Long id = datasetBase();
        // FUNDAMENTAL pero texto libre irrepresentable, frente a IMPORTANTE
        // booleano: gana el que SÍ se puede dibujar.
        campo(id, "textoFundamental", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.FUNDAMENTAL);
        campo(id, "boolImportante", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.IMPORTANTE);
        poblar(id, 20, Map.of(
                "textoFundamental", i -> "Observación clínica distinta número " + i,
                "boolImportante", i -> i % 3 == 0 ? "SI" : "NO"));

        List<String> orden = codigos(propuestaDashboardService.proponer(id));

        assertThat(orden.indexOf("porcentaje_bool_importante"))
                .isLessThan(orden.indexOf("completitud_texto_fundamental"));
    }

    // ==================================================================
    // C y D — EXCLUIR no aparece en ninguna parte
    // ==================================================================

    @Test
    void C_unCampoExcluidoNoSeProponeComoWidget() {
        Long id = datasetBase();
        campo(id, "excluido", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.EXCLUIR);
        campo(id, "visible", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);
        poblar(id, 12, Map.of(
                "excluido", i -> "V" + (i % 3),
                "visible", i -> "W" + (i % 3)));

        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(id);

        assertThat(propuestaDe(r, "excluido")).isNull();
        // El equivalente no excluido sí se propone: se ha apartado esa columna,
        // no ese tipo de widget.
        assertThat(propuestaDe(r, "visible")).isNotNull();
    }

    @Test
    void D_unCampoExcluidoNoApareceComoFiltroNiComoDimension() {
        Long id = datasetBase();
        campo(id, "excluido", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.EXCLUIR);
        campo(id, "visible", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);
        poblar(id, 12, Map.of(
                "excluido", i -> "V" + (i % 3),
                "visible", i -> "W" + (i % 3)));

        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(id);

        assertThat(r.getFiltrosRecomendados()).extracting(CampoRecomendadoDto::getCodigo)
                .doesNotContain("excluido").contains("visible");
        assertThat(r.getDimensionesRecomendadas()).extracting(CampoRecomendadoDto::getCodigo)
                .doesNotContain("excluido").contains("visible");
    }

    @Test
    void D_unaFechaExcluidaNoGeneraEvolucionTemporal() {
        Long id = crearDataset();
        campo(id, "pacienteCodigo", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, true, false);
        // La única fecha del dataset, excluida: el estructural no debe colarse.
        campo(id, "fechaEvento", TipoDatoExcel.FECHA, PrioridadDashboardCampo.EXCLUIR, true, false);
        campo(id, "otro", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);
        poblar(id, 10, Map.of("otro", i -> "V" + (i % 3)));

        assertThat(codigos(propuestaDashboardService.proponer(id))).doesNotContain("registros_por_mes");
    }

    @Test
    void D_unIdentificadorExcluidoNoGeneraPacientesUnicos() {
        Long id = crearDataset();
        campo(id, "pacienteCodigo", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.EXCLUIR, true, false);
        campo(id, "fechaEvento", TipoDatoExcel.FECHA, PrioridadDashboardCampo.NORMAL, true, false);
        campo(id, "otro", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);
        poblar(id, 10, Map.of("otro", i -> "V" + (i % 3)));

        assertThat(codigos(propuestaDashboardService.proponer(id))).doesNotContain("pacientes_unicos");
    }

    // ==================================================================
    // E y F — aptitud: qué NO se propone como gráfico
    // ==================================================================

    @Test
    void E_unIdentificadorNoSeProponeComoDistribucionCategorica() {
        Long id = datasetBase();
        campo(id, "otro", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);
        poblar(id, 12, Map.of("otro", i -> "V" + (i % 3)));

        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(id);

        assertThat(codigos(r)).doesNotContain("distribucion_paciente_codigo");
        // Sí se cuenta en distintos, que es su uso correcto.
        assertThat(codigos(r)).contains("pacientes_unicos");
        assertThat(r.getDimensionesRecomendadas()).extracting(CampoRecomendadoDto::getCodigo)
                .doesNotContain("pacienteCodigo");
    }

    @Test
    void F_unTextoLibreDeAltaCardinalidadNoSeProponeComoGrafico() {
        Long id = datasetBase();
        campo(id, "observaciones", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.FUNDAMENTAL);
        // Un valor distinto por registro: no es una categoría.
        poblar(id, 30, Map.of("observaciones", i -> "Nota clínica irrepetible " + i));

        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(id);
        PropuestaWidgetDto p = propuestaDe(r, "observaciones");

        // Aunque sea FUNDAMENTAL: solo su completitud dice algo.
        if (p != null) {
            assertThat(p.getTipoMetrica()).isEqualTo(TipoMetrica.COMPLETITUD.name());
            assertThat(p.getTipoVisualizacion()).isEqualTo(TipoVisualizacion.KPI.name());
        }
        assertThat(r.getDimensionesRecomendadas()).extracting(CampoRecomendadoDto::getCodigo)
                .doesNotContain("observaciones");
    }

    // ==================================================================
    // G y H — representación por tipo
    // ==================================================================

    @Test
    void G_unBooleanoPrioritarioGeneraUnPorcentaje() {
        Long id = datasetBase();
        campo(id, "infeccion", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
        poblar(id, 20, Map.of("infeccion", i -> i % 4 == 0 ? "SI" : "NO"));

        PropuestaWidgetDto p = propuestaDe(propuestaDashboardService.proponer(id), "infeccion");

        assertThat(p).isNotNull();
        assertThat(p.getTipoMetrica()).isEqualTo(TipoMetrica.PORCENTAJE.name());
        assertThat(p.getUnidad()).isEqualTo("%");
        assertThat(p.getTipoVisualizacion()).isEqualTo(TipoVisualizacion.KPI.name());
        // Numerador y denominador explícitos: un porcentaje clínico sin saber
        // sobre cuántos casos se calcula no es interpretable.
        assertThat(p.getConfiguracion().getNumerador()).isNotNull();
        assertThat(p.getConfiguracion().getDenominador()).isNotNull();
    }

    @Test
    void H_conVariasFechasSoloSeProponeUnaEvolucionTemporal() {
        Long id = datasetBase();
        campo(id, "fechaAlta", TipoDatoExcel.FECHA, PrioridadDashboardCampo.IMPORTANTE);
        campo(id, "fechaIntervencion", TipoDatoExcel.FECHA, PrioridadDashboardCampo.FUNDAMENTAL);
        poblar(id, 20, Map.of(
                "fechaAlta", i -> "2026-0" + ((i % 9) + 1) + "-10",
                "fechaIntervencion", i -> "2026-0" + ((i % 9) + 1) + "-05"));

        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(id);

        long series = r.getPropuestas().stream()
                .filter(p -> TipoResultadoWidget.SERIE_TEMPORAL.name().equals(p.getTipoResultado()))
                .count();

        assertThat(series).isEqualTo(PropuestaDashboardServiceImpl.MAXIMO_SERIES_TEMPORALES);
    }

    @Test
    void unCampoNumericoSeProponeComoMedia() {
        Long id = datasetBase();
        campo(id, "edad", TipoDatoExcel.ENTERO, PrioridadDashboardCampo.IMPORTANTE);
        poblar(id, 20, Map.of("edad", i -> String.valueOf(20 + i)));

        PropuestaWidgetDto p = propuestaDe(propuestaDashboardService.proponer(id), "edad");

        assertThat(p).isNotNull();
        assertThat(p.getTipoMetrica()).isEqualTo(TipoMetrica.PROMEDIO.name());
        assertThat(p.getTipoVisualizacion()).isEqualTo(TipoVisualizacion.KPI.name());
    }

    // ==================================================================
    // I y J — no duplicar, no saturar
    // ==================================================================

    @Test
    void I_ningunCampoGeneraDosWidgets() {
        Long id = datasetBase();
        campo(id, "servicio", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.FUNDAMENTAL);
        campo(id, "infeccion", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
        poblar(id, 20, Map.of(
                "servicio", i -> "S" + (i % 4),
                "infeccion", i -> i % 3 == 0 ? "SI" : "NO"));

        List<String> origenes = propuestaDashboardService.proponer(id).getPropuestas().stream()
                .map(PropuestaWidgetDto::getCampoOrigen)
                .filter(java.util.Objects::nonNull)
                .toList();

        assertThat(origenes).doesNotHaveDuplicates();
    }

    @Test
    void J_nuncaSePasaDelTopeDeWidgets() {
        Long id = datasetBase();
        for (int i = 1; i <= 25; i++) {
            campo(id, "flag" + i, TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
        }
        Map<String, java.util.function.IntFunction<Object>> valores = new HashMap<>();
        for (int j = 1; j <= 25; j++) {
            final int idx = j;
            valores.put("flag" + j, i -> (i + idx) % 2 == 0 ? "SI" : "NO");
        }
        poblar(id, 10, valores);

        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(id);

        assertThat(r.getPropuestas()).hasSizeLessThanOrEqualTo(PropuestaDashboardServiceImpl.MAXIMO_WIDGETS);
        assertThat(r.getMaximoWidgets()).isEqualTo(PropuestaDashboardServiceImpl.MAXIMO_WIDGETS);
    }

    @Test
    void J_seRespetaElTopeDeKpiYDeGraficos() {
        Long id = datasetBase();
        for (int i = 1; i <= 12; i++) {
            campo(id, "flag" + i, TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
            campo(id, "cat" + i, TipoDatoExcel.TEXTO, PrioridadDashboardCampo.FUNDAMENTAL);
        }
        Map<String, java.util.function.IntFunction<Object>> valores = new HashMap<>();
        for (int j = 1; j <= 12; j++) {
            final int idx = j;
            valores.put("flag" + j, i -> (i + idx) % 2 == 0 ? "SI" : "NO");
            valores.put("cat" + j, i -> "C" + ((i + idx) % 3));
        }
        poblar(id, 12, valores);

        List<PropuestaWidgetDto> ps = propuestaDashboardService.proponer(id).getPropuestas();

        long kpis = ps.stream().filter(p -> TipoVisualizacion.KPI.name().equals(p.getTipoVisualizacion())).count();
        long graficos = ps.size() - kpis;

        assertThat(kpis).isLessThanOrEqualTo(PropuestaDashboardServiceImpl.MAXIMO_KPI);
        assertThat(graficos).isLessThanOrEqualTo(PropuestaDashboardServiceImpl.MAXIMO_GRAFICOS);
    }

    @Test
    void J_noSeLlenaElDashboardDeCompletitudes() {
        Long id = datasetBase();
        // Ocho columnas obligatorias casi vacías: sin cupo, ocho completitudes.
        for (int i = 1; i <= 8; i++) {
            campo(id, "vacio" + i, TipoDatoExcel.TEXTO, PrioridadDashboardCampo.IMPORTANTE, false, true);
        }
        poblar(id, 20, Map.of());

        long completitudes = propuestaDashboardService.proponer(id).getPropuestas().stream()
                .filter(p -> TipoMetrica.COMPLETITUD.name().equals(p.getTipoMetrica()))
                .count();

        assertThat(completitudes).isLessThanOrEqualTo(PropuestaDashboardServiceImpl.MAXIMO_COMPLETITUDES);
    }

    @Test
    void unDashboardRicoSaleVariado() {
        Long id = datasetIlqSintetico();

        List<PropuestaWidgetDto> ps = propuestaDashboardService.proponer(id).getPropuestas();
        List<String> tipos = ps.stream().map(PropuestaWidgetDto::getTipoMetrica).distinct().toList();

        // Magnitud, tasa, distribución y evolución: no quince veces lo mismo.
        assertThat(tipos).contains(
                TipoMetrica.CONTEO.name(),
                TipoMetrica.PORCENTAJE.name(),
                TipoMetrica.DISTRIBUCION.name());
        assertThat(ps).anySatisfy(p ->
                assertThat(p.getTipoResultado()).isEqualTo(TipoResultadoWidget.SERIE_TEMPORAL.name()));
    }

    // ==================================================================
    // K — determinismo
    // ==================================================================

    @Test
    void K_dosLlamadasProponenExactamenteLoMismo() {
        Long id = datasetIlqSintetico();

        assertThat(codigos(propuestaDashboardService.proponer(id)))
                .isEqualTo(codigos(propuestaDashboardService.proponer(id)));
    }

    @Test
    void K_elOrdenNoDependeDeLaPosicionDeLasColumnas() {
        Long id = datasetBase();
        // El texto libre se declara ANTES que el booleano fundamental.
        campo(id, "observaciones", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);
        campo(id, "infeccion", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
        poblar(id, 20, Map.of(
                "observaciones", i -> "Texto distinto " + i,
                "infeccion", i -> i % 3 == 0 ? "SI" : "NO"));

        List<String> orden = codigos(propuestaDashboardService.proponer(id));
        int posInfeccion = orden.indexOf("porcentaje_infeccion");

        assertThat(posInfeccion).isGreaterThanOrEqualTo(0);
        int posObservaciones = orden.indexOf("completitud_observaciones");
        if (posObservaciones >= 0) {
            assertThat(posInfeccion).isLessThan(posObservaciones);
        }
    }

    @Test
    void K_elOrdenDeLasPropuestasEsCorrelativoYSinHuecos() {
        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(datasetIlqSintetico());

        for (int i = 0; i < r.getPropuestas().size(); i++) {
            assertThat(r.getPropuestas().get(i).getOrden()).isEqualTo(i + 1);
        }
    }

    @Test
    void losKpiVanDelanteDeLosGraficos() {
        List<PropuestaWidgetDto> ps = propuestaDashboardService.proponer(datasetIlqSintetico()).getPropuestas();

        int ultimoKpi = -1;
        int primerGrafico = Integer.MAX_VALUE;
        for (int i = 0; i < ps.size(); i++) {
            if (TipoVisualizacion.KPI.name().equals(ps.get(i).getTipoVisualizacion())) ultimoKpi = i;
            else if (primerGrafico == Integer.MAX_VALUE) primerGrafico = i;
        }

        assertThat(ultimoKpi).isLessThan(primerGrafico);
    }

    @Test
    void losAnchosSiguenLaRegla() {
        for (PropuestaWidgetDto p : propuestaDashboardService.proponer(datasetIlqSintetico()).getPropuestas()) {
            if (TipoVisualizacion.KPI.name().equals(p.getTipoVisualizacion())) {
                assertThat(p.getAncho()).as("KPI %s", p.getCodigoMetrica()).isEqualTo(3);
            } else if (TipoVisualizacion.DONUT.name().equals(p.getTipoVisualizacion())) {
                assertThat(p.getAncho()).as("donut %s", p.getCodigoMetrica()).isEqualTo(6);
            } else if (TipoVisualizacion.LINEAS.name().equals(p.getTipoVisualizacion())) {
                assertThat(p.getAncho()).as("serie %s", p.getCodigoMetrica()).isEqualTo(12);
            }
        }
    }

    // ==================================================================
    // L — esComun y obligatorio no mandan sobre la relevancia
    // ==================================================================

    @Test
    void L_esComunNoAlteraElRankingSiLaPrioridadEsLaMisma() {
        Long id = datasetBase();
        // Uno común y otro no, ambos NORMAL: deben quedar contiguos y en orden
        // alfabético (el desempate estable), no por ser común.
        campo(id, "aaa", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, false, false);
        campo(id, "bbb", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, true, false);
        poblar(id, 12, Map.of(
                "aaa", i -> "V" + (i % 3),
                "bbb", i -> "W" + (i % 3)));

        List<String> orden = codigos(propuestaDashboardService.proponer(id));

        assertThat(orden.indexOf("distribucion_aaa")).isLessThan(orden.indexOf("distribucion_bbb"));
    }

    @Test
    void L_obligatorioNoAsciendeUnCampoPorEncimaDeSuPrioridad() {
        Long id = datasetBase();
        campo(id, "aaa", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, false, true);
        campo(id, "bbb", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.IMPORTANTE, false, false);
        poblar(id, 12, Map.of(
                "aaa", i -> "V" + (i % 3),
                "bbb", i -> "W" + (i % 3)));

        List<String> orden = codigos(propuestaDashboardService.proponer(id));

        // El IMPORTANTE gana pese a que el otro sea obligatorio.
        assertThat(orden.indexOf("distribucion_bbb")).isLessThan(orden.indexOf("distribucion_aaa"));
    }

    @Test
    void L_obligatorioSigueValiendoParaLaCalidadDelDato() {
        Long id = datasetBase();
        // Campo obligatorio y casi vacío: su vacío ES el hallazgo. Este es el
        // uso legítimo de `obligatorio`, que no se ha tocado.
        campo(id, "sinRellenar", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, false, true);
        poblar(id, 20, Map.of());

        PropuestaWidgetDto p = propuestaDe(propuestaDashboardService.proponer(id), "sinRellenar");

        assertThat(p).isNotNull();
        assertThat(p.getTipoMetrica()).isEqualTo(TipoMetrica.COMPLETITUD.name());
    }

    // ==================================================================
    // M — caso clínico sintético
    // ==================================================================

    /**
     * Dataset con la forma de una vigilancia de ILQ, pero SIN depender de los
     * nombres: lo que decide la propuesta son las prioridades y los roles.
     */
    private Long datasetIlqSintetico() {
        Long id = crearDataset();
        campo(id, "pacienteCodigo", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.IMPORTANTE, true, false);
        campo(id, "fechaEvento", TipoDatoExcel.FECHA, PrioridadDashboardCampo.FUNDAMENTAL, true, false);
        campo(id, "servicio", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.FUNDAMENTAL, true, false);
        campo(id, "procedimiento", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.IMPORTANTE, true, false);
        campo(id, "sexo", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, true, false);
        campo(id, "edad", TipoDatoExcel.ENTERO, PrioridadDashboardCampo.IMPORTANTE, true, false);
        campo(id, "ilq", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
        campo(id, "profilaxisIndicada", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
        campo(id, "profilaxisAdecuada", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.FUNDAMENTAL);
        campo(id, "drago", TipoDatoExcel.BOOLEANO, PrioridadDashboardCampo.IMPORTANTE);
        campo(id, "notas", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL);

        for (int i = 1; i <= 40; i++) {
            // Los campos comunes viven en columnas propias del registro; solo
            // los específicos del dataset van en datosDinamicos.
            RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
            r.setDatasetId(id);
            r.setPacienteCodigo("P" + (i % 25));
            r.setFechaEvento(LocalDate.of(2026, (i % 12) + 1, 10));
            r.setServicio("SERVICIO_" + (i % 4));
            r.setProcedimiento("PROC_" + (i % 6));
            r.setSexo(i % 2 == 0 ? "MUJER" : "HOMBRE");
            r.setEdad(30 + (i % 50));

            Map<String, Object> d = new HashMap<>();
            d.put("ilq", i % 8 == 0 ? "SI" : "NO");
            d.put("profilaxisIndicada", i % 5 == 0 ? "NO" : "SI");
            d.put("profilaxisAdecuada", i % 4 == 0 ? "NO" : "SI");
            d.put("drago", i % 6 == 0 ? "NO" : "SI");
            d.put("notas", "Nota irrepetible " + i);
            r.setDatosDinamicos(d);

            registroClinicoGenericoService.crear(r);
        }
        return id;
    }

    @Test
    void M_elCasoIlqProduceUnaPropuestaClinicamenteRazonable() {
        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(datasetIlqSintetico());
        List<String> cs = codigos(r);

        // Volumen y denominador clínico.
        assertThat(cs).contains("total_registros", "pacientes_unicos");
        // Las tres tasas fundamentales.
        assertThat(cs).contains("porcentaje_ilq", "porcentaje_profilaxis_indicada",
                "porcentaje_profilaxis_adecuada");
        // Evolución temporal.
        assertThat(cs).contains("registros_por_mes");
        // Distribución por una dimensión clínica útil.
        assertThat(cs).contains("distribucion_servicio");
        // El texto libre NO produce un gráfico categórico.
        assertThat(cs).doesNotContain("distribucion_notas");
        // Tamaño razonable para leerse de un vistazo.
        assertThat(r.getPropuestas()).hasSizeBetween(8, PropuestaDashboardServiceImpl.MAXIMO_WIDGETS);
    }

    @Test
    void M_losFiltrosRecomendadosSonLosClinicamenteUtiles() {
        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(datasetIlqSintetico());
        List<String> filtros = r.getFiltrosRecomendados().stream()
                .map(CampoRecomendadoDto::getCodigo).toList();

        assertThat(filtros).contains("servicio", "fechaEvento");
        assertThat(filtros).doesNotContain("pacienteCodigo", "notas");
        assertThat(r.getFiltrosRecomendados()).allSatisfy(f ->
                assertThat(f.getMotivo()).isNotBlank());
    }

    @Test
    void M_lasDimensionesRecomendadasExcluyenFechasEIdentificadores() {
        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(datasetIlqSintetico());
        List<String> dims = r.getDimensionesRecomendadas().stream()
                .map(CampoRecomendadoDto::getCodigo).toList();

        assertThat(dims).contains("servicio", "sexo");
        // Agrupar por fecha exacta daría una categoría por día.
        assertThat(dims).doesNotContain("fechaEvento", "pacienteCodigo", "notas");
    }

    @Test
    void losMotivosSonClinicosYNoMuestranPuntuaciones() {
        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(datasetIlqSintetico());

        assertThat(r.getPropuestas()).allSatisfy(p -> {
            assertThat(p.getMotivo()).isNotBlank();
            // Nada de "340 puntos" ni "score": al usuario le sirve el porqué.
            assertThat(p.getMotivo().toLowerCase()).doesNotContain("punt", "score", "peso");
        });
    }

    // ==================================================================
    // Datasets insuficientes
    // ==================================================================

    @Test
    void unDatasetSinRegistrosNoTieneCamposSuficientes() {
        Long id = datasetBase();

        PropuestaDashboardResponseDto r = propuestaDashboardService.proponer(id);

        assertThat(r.getSuficiente()).isFalse();
        assertThat(r.getMotivoInsuficiente()).contains("registros");
        assertThat(r.getPropuestas()).isEmpty();
    }

    @Test
    void unDatasetConUnaSolaColumnaNoTieneCamposSuficientes() {
        Long id = crearDataset();
        campo(id, "pacienteCodigo", TipoDatoExcel.TEXTO, PrioridadDashboardCampo.NORMAL, true, false);

        assertThat(propuestaDashboardService.proponer(id).getSuficiente()).isFalse();
    }

    // ==================================================================
    // N — aplicar la propuesta persiste correctamente
    // ==================================================================

    private record Contexto(Long datasetId, Long panelId, Long metricaId, Long widgetId) {
    }

    private ConfiguracionMetricaDto configCampoValor(String campo) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setCampoValor(campo);
        return c;
    }

    private Contexto contextoConWidget(TipoMetrica tipo, ConfiguracionMetricaDto config) {
        Long datasetId = datasetIlqSintetico();

        PanelClinicoRequestDto panelRequest = new PanelClinicoRequestDto();
        panelRequest.setCodigo(codigoUnico("PANEL"));
        panelRequest.setNombre("Panel de prueba");
        Long panelId = panelClinicoService.crear(datasetId, panelRequest).getId();

        MetricaClinicaRequestDto metricaRequest = new MetricaClinicaRequestDto();
        metricaRequest.setCodigo(codigoUnico("M"));
        metricaRequest.setNombre("Métrica de prueba");
        metricaRequest.setTipoMetrica(tipo);
        metricaRequest.setConfiguracion(config);
        Long metricaId = metricaClinicaService.crear(datasetId, metricaRequest).getId();

        PanelMetricaRequestDto widgetRequest = new PanelMetricaRequestDto();
        widgetRequest.setMetricaId(metricaId);
        widgetRequest.setTipoVisualizacion(TipoVisualizacion.KPI);
        widgetRequest.setAncho(3);
        widgetRequest.setOrden(1);
        Long widgetId = panelMetricaService.crear(panelId, widgetRequest).getId();

        return new Contexto(datasetId, panelId, metricaId, widgetId);
    }

    @Test
    void N_aplicarUnaPropuestaCreaMetricaYWidgetUtilizables() {
        Long datasetId = datasetIlqSintetico();
        PropuestaWidgetDto propuesta = propuestaDashboardService.proponer(datasetId).getPropuestas().stream()
                .filter(p -> TipoMetrica.PORCENTAJE.name().equals(p.getTipoMetrica()))
                .findFirst().orElseThrow();

        PanelClinicoRequestDto panelRequest = new PanelClinicoRequestDto();
        panelRequest.setCodigo(codigoUnico("PANEL"));
        panelRequest.setNombre("Dashboard recomendado");
        Long panelId = panelClinicoService.crear(datasetId, panelRequest).getId();

        MetricaClinicaRequestDto m = new MetricaClinicaRequestDto();
        m.setCodigo(propuesta.getCodigoMetrica());
        m.setNombre(propuesta.getNombre());
        m.setTipoMetrica(TipoMetrica.valueOf(propuesta.getTipoMetrica()));
        m.setConfiguracion(propuesta.getConfiguracion());
        m.setUnidad(propuesta.getUnidad());
        m.setDecimales(propuesta.getDecimales());
        Long metricaId = metricaClinicaService.crear(datasetId, m).getId();

        PanelMetricaRequestDto w = new PanelMetricaRequestDto();
        w.setMetricaId(metricaId);
        w.setTipoVisualizacion(TipoVisualizacion.valueOf(propuesta.getTipoVisualizacion()));
        w.setAncho(propuesta.getAncho());
        w.setOrden(propuesta.getOrden());
        panelMetricaService.crear(panelId, w);

        // El widget se ejecuta sin error: la configuración propuesta es válida
        // para el motor, no solo bien formada.
        DashboardPanelResponseDto dashboard =
                dashboardPanelService.obtenerDashboard(panelId, new DashboardPanelRequestDto());

        assertThat(dashboard.getWidgets()).hasSize(1);
        assertThat(dashboard.getWidgets().get(0).getEstado()).isEqualTo("OK");
        assertThat(dashboard.getWidgets().get(0).getAncho()).isEqualTo(propuesta.getAncho());
    }

    @Test
    void N_editarPersisteAnchoVisualizacionYFormaALaVez() {
        Contexto c = contextoConWidget(TipoMetrica.PROMEDIO, configCampoValor("edad"));

        PanelMetricaRequestDto presentacion = new PanelMetricaRequestDto();
        presentacion.setMetricaId(c.metricaId());
        presentacion.setTipoVisualizacion(TipoVisualizacion.BARRAS);
        presentacion.setAncho(6);
        presentacion.setOrden(1);
        panelMetricaService.actualizar(c.panelId(), c.widgetId(), presentacion);

        PanelMetricaConfiguracionWidgetRequestDto resultado = new PanelMetricaConfiguracionWidgetRequestDto();
        resultado.setTipoResultado(TipoResultadoWidget.COMPARATIVA);
        ConfiguracionWidgetDto cw = new ConfiguracionWidgetDto();
        cw.setCampoAgrupacion("sexo");
        resultado.setConfiguracionWidget(cw);
        panelMetricaService.actualizarConfiguracionWidget(c.panelId(), c.widgetId(), resultado);

        PanelMetricaResponseDto persistido = panelMetricaService.listarPorPanel(c.panelId()).stream()
                .filter(w -> w.getId().equals(c.widgetId()))
                .findFirst().orElseThrow();

        assertThat(persistido.getAncho()).isEqualTo(6);
        assertThat(persistido.getTipoVisualizacion()).isEqualTo("BARRAS");
        assertThat(persistido.getTipoResultadoWidget()).isEqualTo("COMPARATIVA");
        assertThat(persistido.getConfiguracionWidget().getCampoAgrupacion()).isEqualTo("sexo");
    }

    @Test
    void N_configurarLaFormaNoPisaElAnchoYaGuardado() {
        Contexto c = contextoConWidget(TipoMetrica.PROMEDIO, configCampoValor("edad"));

        PanelMetricaRequestDto presentacion = new PanelMetricaRequestDto();
        presentacion.setMetricaId(c.metricaId());
        presentacion.setTipoVisualizacion(TipoVisualizacion.TABLA);
        presentacion.setAncho(12);
        presentacion.setOrden(1);
        panelMetricaService.actualizar(c.panelId(), c.widgetId(), presentacion);

        PanelMetricaConfiguracionWidgetRequestDto resultado = new PanelMetricaConfiguracionWidgetRequestDto();
        resultado.setTipoResultado(TipoResultadoWidget.COMPARATIVA);
        ConfiguracionWidgetDto cw = new ConfiguracionWidgetDto();
        cw.setCampoAgrupacion("sexo");
        resultado.setConfiguracionWidget(cw);
        panelMetricaService.actualizarConfiguracionWidget(c.panelId(), c.widgetId(), resultado);

        assertThat(panelMetricaService.listarPorPanel(c.panelId()).get(0).getAncho()).isEqualTo(12);
    }

    @Test
    void N_editarUnWidgetNoCreaOtro() {
        Contexto c = contextoConWidget(TipoMetrica.CONTEO, new ConfiguracionMetricaDto());

        for (int ancho : new int[] {6, 12}) {
            PanelMetricaRequestDto p = new PanelMetricaRequestDto();
            p.setMetricaId(c.metricaId());
            p.setTipoVisualizacion(TipoVisualizacion.BARRAS);
            p.setAncho(ancho);
            p.setOrden(1);
            panelMetricaService.actualizar(c.panelId(), c.widgetId(), p);
        }

        assertThat(panelMetricaService.listarPorPanel(c.panelId())).hasSize(1);
        assertThat(panelMetricaService.listarPorPanel(c.panelId()).get(0).getAncho()).isEqualTo(12);
    }

    @Test
    void N_quitarUnWidgetNoArchivaLaMetrica() {
        Contexto c = contextoConWidget(TipoMetrica.CONTEO, new ConfiguracionMetricaDto());

        panelMetricaService.desactivar(c.panelId(), c.widgetId());

        assertThat(panelMetricaService.listarPorPanel(c.panelId())).isEmpty();
        assertThat(metricaClinicaService.obtenerPorId(c.metricaId()).getActiva()).isTrue();
    }

    @Test
    void N_noSePuedeAnadirDosVecesLaMismaMetricaAlMismoPanel() {
        Contexto c = contextoConWidget(TipoMetrica.CONTEO, new ConfiguracionMetricaDto());

        PanelMetricaRequestDto duplicado = new PanelMetricaRequestDto();
        duplicado.setMetricaId(c.metricaId());
        duplicado.setTipoVisualizacion(TipoVisualizacion.TABLA);
        duplicado.setAncho(6);
        duplicado.setOrden(2);

        assertThatThrownBy(() -> panelMetricaService.crear(c.panelId(), duplicado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya está activa en este panel");
    }

    @Test
    void N_unaMetricaSiPuedeEstarEnDosPanelesDistintos() {
        Contexto c = contextoConWidget(TipoMetrica.CONTEO, new ConfiguracionMetricaDto());

        PanelClinicoRequestDto otro = new PanelClinicoRequestDto();
        otro.setCodigo(codigoUnico("PANEL2"));
        otro.setNombre("Segundo panel");
        Long otroPanelId = panelClinicoService.crear(c.datasetId(), otro).getId();

        PanelMetricaRequestDto request = new PanelMetricaRequestDto();
        request.setMetricaId(c.metricaId());
        request.setTipoVisualizacion(TipoVisualizacion.KPI);
        request.setAncho(3);
        request.setOrden(1);

        assertThat(panelMetricaService.crear(otroPanelId, request).getId()).isNotNull();
    }
}
