package com.preventiva.backend;

import com.preventiva.backend.dto.AplicacionDashboardIlqDto;
import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CompatibilidadDashboardIlqDto;
import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardPanelResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.MetricaClinicaResponseDto;
import com.preventiva.backend.dto.PanelMetricaResponseDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DashboardPanelService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.PanelClinicoService;
import com.preventiva.backend.service.interfaces.PanelMetricaService;
import com.preventiva.backend.service.interfaces.PlantillaDashboardIlqService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import com.preventiva.backend.util.PlantillaDashboardIlq;

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
 * Fase 6.9I.4 — aplicación del dashboard clínico de ILQ desde la aplicación.
 *
 * <p>Comprueba la compatibilidad, la aplicación completa, la idempotencia y que
 * no se toque nada fuera del dataset indicado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlantillaDashboardIlqIntegrationTest {

    private static final String PREFIJO = "TEST_69I4_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private MetricaClinicaService metricaClinicaService;
    @Autowired private PanelClinicoService panelClinicoService;
    @Autowired private PanelMetricaService panelMetricaService;
    @Autowired private DashboardPanelService dashboardPanelService;
    @Autowired private PlantillaDashboardIlqService plantillaService;

    // ------------------------------------------------------------------

    private String codigoUnico(String sufijo) {
        return PREFIJO + sufijo + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(codigoUnico("DS"));
        r.setNombre("Test 69I4");
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

    /** Dataset con TODOS los campos esenciales de la plantilla. */
    private Long datasetCompatible() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo(datasetId, "procedimiento", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "infeccionLocalizacionQuirurgica", TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, "localizacionInfeccion", TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, "profilaxisIndicada", TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, "adecuacionProfilaxis", TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, "motivoInadecuacionProfilaxis", TipoDatoExcel.TEXTO, false);
        crearCampo(datasetId, "prescripcionProfilaxisDrago", TipoDatoExcel.BOOLEANO, false);

        // 4 intervenciones, 3 pacientes; 1 ILQ documentada como sí, 1 como no,
        // 1 sin documentar. Suficiente para que las 14 métricas se ejecuten.
        registro(datasetId, "P1", LocalDate.of(2026, 1, 10), "COLECISTECTOMIA", "SI", "ORGANO_ESPACIO",
                "SI", "ADECUADA", null, "SI");
        registro(datasetId, "P1", LocalDate.of(2026, 2, 10), "COLECISTECTOMIA", "NO", null,
                "SI", "INADECUADA", "MOMENTO_INADECUADO", "NO");
        registro(datasetId, "P2", LocalDate.of(2026, 3, 10), "APENDICECTOMIA", "NO", null,
                "SI", "ADECUADA", null, null);
        registro(datasetId, "P3", LocalDate.of(2026, 4, 10), "APENDICECTOMIA", null, null,
                "NO", "NO_APLICA", null, null);

        return datasetId;
    }

    private void registro(
            Long datasetId, String paciente, LocalDate fecha, String procedimiento, String ilq,
            String localizacion, String profilaxis, String adecuacion, String motivo, String drago) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setFechaEvento(fecha);
        r.setProcedimiento(procedimiento);

        Map<String, Object> d = new HashMap<>();
        if (ilq != null) d.put("infeccionLocalizacionQuirurgica", ilq);
        if (localizacion != null) d.put("localizacionInfeccion", localizacion);
        if (profilaxis != null) d.put("profilaxisIndicada", profilaxis);
        if (adecuacion != null) d.put("adecuacionProfilaxis", adecuacion);
        if (motivo != null) d.put("motivoInadecuacionProfilaxis", motivo);
        if (drago != null) d.put("prescripcionProfilaxisDrago", drago);
        r.setDatosDinamicos(d);

        registroClinicoGenericoService.crear(r);
    }

    private List<MetricaClinicaResponseDto> metricasPlantilla(Long datasetId) {
        return metricaClinicaService.listarPorDataset(datasetId).stream()
                .filter(m -> m.getCodigo().startsWith(PlantillaDashboardIlq.PREFIJO))
                .toList();
    }

    // ------------------------------------------------------------------
    // Compatibilidad
    // ------------------------------------------------------------------

    @Test
    void unDatasetConTodosLosCamposEsCompatible() {
        CompatibilidadDashboardIlqDto c = plantillaService.comprobarCompatibilidad(datasetCompatible());

        assertThat(c.getCompatible()).isTrue();
        assertThat(c.getCamposAusentes()).isEmpty();
        assertThat(c.getCamposEncontrados()).hasSize(PlantillaDashboardIlq.CAMPOS_ESENCIALES.size());
        assertThat(c.getYaAplicado()).isFalse();
        assertThat(c.getTotalElementos()).isEqualTo(14);
    }

    @Test
    void unDatasetSinLosCamposClinicosNoEsCompatibleYDiceCualesFaltan() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);

        CompatibilidadDashboardIlqDto c = plantillaService.comprobarCompatibilidad(datasetId);

        assertThat(c.getCompatible()).isFalse();
        assertThat(c.getCamposEncontrados()).containsExactlyInAnyOrder("pacienteCodigo", "fechaEvento");
        assertThat(c.getCamposAusentes()).contains(
                "infeccionLocalizacionQuirurgica", "adecuacionProfilaxis", "prescripcionProfilaxisDrago");
    }

    @Test
    void unCampoDesactivadoDejaDeContarComoPresente() {
        Long datasetId = datasetCompatible();

        Long campoId = campoClinicoService.listarPorDataset(datasetId).stream()
                .filter(c -> c.getCodigo().equals("adecuacionProfilaxis"))
                .findFirst().orElseThrow().getId();
        campoClinicoService.desactivar(datasetId, campoId);

        CompatibilidadDashboardIlqDto c = plantillaService.comprobarCompatibilidad(datasetId);

        assertThat(c.getCompatible()).isFalse();
        assertThat(c.getCamposAusentes()).contains("adecuacionProfilaxis");
    }

    // ------------------------------------------------------------------
    // Aplicación
    // ------------------------------------------------------------------

    @Test
    void aplicarCrea14MetricasY14Widgets() {
        Long datasetId = datasetCompatible();

        AplicacionDashboardIlqDto r = plantillaService.aplicar(datasetId);

        assertThat(r.getPanelCreado()).isTrue();
        assertThat(r.getPanelCodigo()).isEqualTo(PlantillaDashboardIlq.CODIGO_PANEL);
        assertThat(r.getMetricasCreadas()).isEqualTo(14);
        assertThat(r.getWidgetsCreados()).isEqualTo(14);
        assertThat(r.getTotalMetricas()).isEqualTo(14);
        assertThat(r.getTotalWidgets()).isEqualTo(14);
    }

    @Test
    void elPanelTieneOchoKpiYSeisGraficos() {
        Long datasetId = datasetCompatible();
        AplicacionDashboardIlqDto r = plantillaService.aplicar(datasetId);

        List<PanelMetricaResponseDto> widgets = panelMetricaService.listarPorPanel(r.getPanelId());

        assertThat(widgets).hasSize(14);
        assertThat(widgets.stream().filter(w -> w.getTipoVisualizacion().equals("KPI"))).hasSize(8);
        assertThat(widgets.stream().filter(w -> !w.getTipoVisualizacion().equals("KPI"))).hasSize(6);
    }

    @Test
    void todosLosWidgetsSeEjecutanSinError() {
        Long datasetId = datasetCompatible();
        AplicacionDashboardIlqDto r = plantillaService.aplicar(datasetId);

        DashboardPanelResponseDto dashboard =
                dashboardPanelService.obtenerDashboard(r.getPanelId(), new DashboardPanelRequestDto());

        assertThat(dashboard.getWidgets()).hasSize(14);
        assertThat(dashboard.getWidgets())
                .as("widgets en error: %s", dashboard.getWidgets().stream()
                        .filter(w -> !"OK".equals(w.getEstado()))
                        .map(w -> w.getCodigo() + " -> "
                                + (w.getError() != null ? w.getError().getMensaje() : "?"))
                        .toList())
                .allMatch(w -> "OK".equals(w.getEstado()));
    }

    @Test
    void aplicarSobreUnDatasetIncompletoNoCreaNada() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);

        assertThatThrownBy(() -> plantillaService.aplicar(datasetId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no contiene todos los campos necesarios");

        assertThat(metricasPlantilla(datasetId)).isEmpty();
        assertThat(panelClinicoService.listarPorDataset(datasetId)).isEmpty();
    }

    @Test
    void aplicarSobreUnDatasetInexistenteFalla() {
        assertThatThrownBy(() -> plantillaService.aplicar(-1L))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ------------------------------------------------------------------
    // Idempotencia
    // ------------------------------------------------------------------

    @Test
    void aplicarDosVecesNoDuplicaNada() {
        Long datasetId = datasetCompatible();

        plantillaService.aplicar(datasetId);
        AplicacionDashboardIlqDto segunda = plantillaService.aplicar(datasetId);

        assertThat(segunda.getPanelCreado()).isFalse();
        assertThat(segunda.getMetricasCreadas()).isZero();
        assertThat(segunda.getWidgetsCreados()).isZero();
        assertThat(segunda.getMetricasActualizadas()).isEqualTo(14);
        assertThat(segunda.getWidgetsActualizados()).isEqualTo(14);
        assertThat(segunda.getTotalMetricas()).isEqualTo(14);
        assertThat(segunda.getTotalWidgets()).isEqualTo(14);
    }

    @Test
    void aplicarDosVecesConservaLosMismosIdentificadores() {
        Long datasetId = datasetCompatible();

        AplicacionDashboardIlqDto primera = plantillaService.aplicar(datasetId);
        List<Long> idsMetricas = metricasPlantilla(datasetId).stream()
                .map(MetricaClinicaResponseDto::getId).sorted().toList();

        AplicacionDashboardIlqDto segunda = plantillaService.aplicar(datasetId);

        // Mismo id de panel: el enlace al dashboard sigue siendo válido.
        assertThat(segunda.getPanelId()).isEqualTo(primera.getPanelId());
        assertThat(metricasPlantilla(datasetId).stream()
                .map(MetricaClinicaResponseDto::getId).sorted().toList())
                .isEqualTo(idsMetricas);
    }

    @Test
    void reaplicarConservaElTituloPersonalizadoDelUsuario() {
        Long datasetId = datasetCompatible();
        AplicacionDashboardIlqDto primera = plantillaService.aplicar(datasetId);

        PanelMetricaResponseDto widget = panelMetricaService.listarPorPanel(primera.getPanelId()).stream()
                .filter(w -> w.getMetricaCodigo().equals("mvp_ilq_casos"))
                .findFirst().orElseThrow();

        com.preventiva.backend.dto.PanelMetricaRequestDto edicion =
                new com.preventiva.backend.dto.PanelMetricaRequestDto();
        edicion.setMetricaId(widget.getMetricaId());
        edicion.setTituloPersonalizado("Infecciones de mi servicio");
        edicion.setTipoVisualizacion(com.preventiva.backend.enums.TipoVisualizacion.KPI);
        edicion.setAncho(3);
        edicion.setOrden(3);
        panelMetricaService.actualizar(primera.getPanelId(), widget.getId(), edicion);

        plantillaService.aplicar(datasetId);

        PanelMetricaResponseDto tras = panelMetricaService.listarPorPanel(primera.getPanelId()).stream()
                .filter(w -> w.getMetricaCodigo().equals("mvp_ilq_casos"))
                .findFirst().orElseThrow();

        // La plantilla corrige la fórmula clínica, no cómo el usuario decidió
        // llamar a su widget.
        assertThat(tras.getTituloPersonalizado()).isEqualTo("Infecciones de mi servicio");
    }

    @Test
    void reaplicarCorrigeLaFormulaClinicaAunqueSeHubieraEditado() {
        Long datasetId = datasetCompatible();
        plantillaService.aplicar(datasetId);

        MetricaClinicaResponseDto tasa = metricasPlantilla(datasetId).stream()
                .filter(m -> m.getCodigo().equals("mvp_ilq_tasa"))
                .findFirst().orElseThrow();

        com.preventiva.backend.dto.MetricaClinicaRequestDto edicion =
                new com.preventiva.backend.dto.MetricaClinicaRequestDto();
        edicion.setCodigo(tasa.getCodigo());
        edicion.setNombre("Tasa manipulada");
        edicion.setTipoMetrica(com.preventiva.backend.enums.TipoMetrica.CONTEO);
        edicion.setConfiguracion(new com.preventiva.backend.dto.ConfiguracionMetricaDto());
        metricaClinicaService.actualizar(tasa.getId(), edicion);

        plantillaService.aplicar(datasetId);

        MetricaClinicaResponseDto restaurada = metricasPlantilla(datasetId).stream()
                .filter(m -> m.getCodigo().equals("mvp_ilq_tasa"))
                .findFirst().orElseThrow();

        assertThat(restaurada.getTipoMetrica()).isEqualTo("PORCENTAJE");
        assertThat(restaurada.getNombre()).isEqualTo("Tasa de ILQ");
        assertThat(restaurada.getConfiguracion().getDenominador()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Aislamiento
    // ------------------------------------------------------------------

    @Test
    void aplicarNoTocaOtrosDatasets() {
        Long ajeno = datasetCompatible();
        plantillaService.aplicar(ajeno);
        int metricasAjenas = metricasPlantilla(ajeno).size();
        int panelesAjenos = panelClinicoService.listarPorDataset(ajeno).size();

        Long propio = datasetCompatible();
        plantillaService.aplicar(propio);

        assertThat(metricasPlantilla(ajeno)).hasSize(metricasAjenas);
        assertThat(panelClinicoService.listarPorDataset(ajeno)).hasSize(panelesAjenos);
    }

    @Test
    void aplicarNoTocaLasMetricasQueElUsuarioYaTenia() {
        Long datasetId = datasetCompatible();

        com.preventiva.backend.dto.MetricaClinicaRequestDto propia =
                new com.preventiva.backend.dto.MetricaClinicaRequestDto();
        propia.setCodigo("mi_metrica_propia");
        propia.setNombre("Mi métrica");
        propia.setTipoMetrica(com.preventiva.backend.enums.TipoMetrica.CONTEO);
        propia.setConfiguracion(new com.preventiva.backend.dto.ConfiguracionMetricaDto());
        Long propiaId = metricaClinicaService.crear(datasetId, propia).getId();

        plantillaService.aplicar(datasetId);

        MetricaClinicaResponseDto tras = metricaClinicaService.obtenerPorId(propiaId);
        assertThat(tras.getNombre()).isEqualTo("Mi métrica");
        assertThat(tras.getActiva()).isTrue();
    }

    @Test
    void aplicarNoTocaLasColumnasNiLosRegistros() {
        Long datasetId = datasetCompatible();
        int campos = campoClinicoService.listarPorDataset(datasetId).size();

        plantillaService.aplicar(datasetId);
        plantillaService.aplicar(datasetId);

        assertThat(campoClinicoService.listarPorDataset(datasetId)).hasSize(campos);
    }

    // ------------------------------------------------------------------
    // La plantilla como fuente de verdad
    // ------------------------------------------------------------------

    @Test
    void laPlantillaDefineCatorceElementosConCodigosUnicosYPrefijados() {
        List<PlantillaDashboardIlq.ElementoPlantilla> elementos = PlantillaDashboardIlq.elementos();

        assertThat(elementos).hasSize(14);
        assertThat(elementos.stream().map(PlantillaDashboardIlq.ElementoPlantilla::codigo).distinct()).hasSize(14);
        assertThat(elementos).allSatisfy(e ->
                assertThat(e.codigo()).startsWith(PlantillaDashboardIlq.PREFIJO));
        assertThat(elementos.stream().map(PlantillaDashboardIlq.ElementoPlantilla::orden).distinct()).hasSize(14);
    }

    @Test
    void todoCampoUsadoPorLaPlantillaEstaDeclaradoComoEsencial() {
        // Si una configuración usara un campo no declarado esencial, la
        // comprobación de compatibilidad daría el visto bueno a un dataset que
        // luego produciría widgets en error.
        assertThat(PlantillaDashboardIlq.CAMPOS_ESENCIALES)
                .contains("pacienteCodigo", "fechaEvento", "procedimiento",
                        "infeccionLocalizacionQuirurgica", "localizacionInfeccion",
                        "profilaxisIndicada", "adecuacionProfilaxis",
                        "motivoInadecuacionProfilaxis", "prescripcionProfilaxisDrago");
    }
}
