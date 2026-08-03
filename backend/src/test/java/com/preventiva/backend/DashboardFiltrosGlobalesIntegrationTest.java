package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.DashboardPanelResponseDto;
import com.preventiva.backend.dto.DashboardWidgetDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.PanelMetricaRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DashboardPanelService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.PanelClinicoService;
import com.preventiva.backend.service.interfaces.PanelMetricaService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 6.9F: filtros globales del dashboard (por campo clínico) y endpoint de
 * valores únicos usado para poblar los selectores del filtro global.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardFiltrosGlobalesIntegrationTest {

    private static final String PREFIJO = "TEST_69F_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired
    private DatasetClinicoService datasetClinicoService;
    @Autowired
    private CampoClinicoService campoClinicoService;
    @Autowired
    private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired
    private MetricaClinicaService metricaClinicaService;
    @Autowired
    private PanelClinicoService panelClinicoService;
    @Autowired
    private PanelMetricaService panelMetricaService;
    @Autowired
    private DashboardPanelService dashboardPanelService;

    private String codigoUnico(String sufijo) {
        return PREFIJO + sufijo + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    private Long crearDatasetActivo(String sufijo) {
        DatasetClinicoRequestDto request = new DatasetClinicoRequestDto();
        request.setCodigo(codigoUnico(sufijo));
        request.setNombre("Test 69F " + sufijo);
        return datasetClinicoService.crear(request).getId();
    }

    private void crearCampoComun(Long datasetId, String codigo, TipoDatoExcel tipoDato) {
        CampoClinicoRequestDto request = new CampoClinicoRequestDto();
        request.setCodigo(codigo);
        request.setEtiqueta(codigo);
        request.setTipoDato(tipoDato);
        request.setEsComun(true);
        request.setObligatorio(false);
        campoClinicoService.crear(datasetId, request);
    }

    private void crearRegistro(Long datasetId, String pacienteCodigo, String sexo, String procedimiento, LocalDate fecha) {
        RegistroClinicoGenericoRequestDto request = new RegistroClinicoGenericoRequestDto();
        request.setDatasetId(datasetId);
        request.setPacienteCodigo(pacienteCodigo);
        request.setSexo(sexo);
        request.setProcedimiento(procedimiento);
        request.setFechaEvento(fecha);
        registroClinicoGenericoService.crear(request);
    }

    private Long crearMetricaConteo(Long datasetId, String codigo) {
        MetricaClinicaRequestDto request = new MetricaClinicaRequestDto();
        request.setCodigo(codigo);
        request.setNombre(codigo);
        request.setTipoMetrica(TipoMetrica.CONTEO);
        ConfiguracionMetricaDto config = new ConfiguracionMetricaDto();
        config.setFiltros(List.of());
        request.setConfiguracion(config);
        return metricaClinicaService.crear(datasetId, request).getId();
    }

    private Long crearPanel(Long datasetId, String codigo) {
        PanelClinicoRequestDto request = new PanelClinicoRequestDto();
        request.setCodigo(codigo);
        request.setNombre(codigo);
        return panelClinicoService.crear(datasetId, request).getId();
    }

    private Long anadirWidgetKpi(Long panelId, Long metricaId) {
        PanelMetricaRequestDto request = new PanelMetricaRequestDto();
        request.setMetricaId(metricaId);
        request.setTipoVisualizacion(TipoVisualizacion.KPI);
        request.setOrden(0);
        request.setAncho(3);
        return panelMetricaService.crear(panelId, request).getId();
    }

    @Test
    void filtroGlobalPorSexo_cambiaElConteoDeCadaWidgetDelDashboard() {
        Long datasetId = crearDatasetActivo("SEXO");
        crearCampoComun(datasetId, "sexo", TipoDatoExcel.TEXTO);
        crearCampoComun(datasetId, "procedimiento", TipoDatoExcel.TEXTO);
        crearCampoComun(datasetId, "fechaEvento", TipoDatoExcel.FECHA);

        crearRegistro(datasetId, "P1", "HOMBRE", "COLECISTECTOMIA", LocalDate.of(2026, 1, 10));
        crearRegistro(datasetId, "P2", "HOMBRE", "APENDICECTOMIA", LocalDate.of(2026, 1, 11));
        crearRegistro(datasetId, "P3", "MUJER", "COLECISTECTOMIA", LocalDate.of(2026, 1, 12));

        Long metricaId = crearMetricaConteo(datasetId, codigoUnico("CONTEO"));
        Long panelId = crearPanel(datasetId, codigoUnico("PANEL"));
        anadirWidgetKpi(panelId, metricaId);

        DashboardPanelResponseDto sinFiltro = dashboardPanelService.obtenerDashboard(panelId, new DashboardPanelRequestDto());
        assertThat(sinFiltro.getWidgets()).hasSize(1);
        assertThat(sinFiltro.getWidgets().get(0).getResultadoActual().getValor()).isEqualTo(3.0);

        DashboardPanelRequestDto filtroHombre = new DashboardPanelRequestDto();
        filtroHombre.setFiltros(List.of(filtro("sexo", OperadorFiltro.EQ, "HOMBRE")));
        DashboardPanelResponseDto conFiltroHombre = dashboardPanelService.obtenerDashboard(panelId, filtroHombre);
        DashboardWidgetDto widgetHombre = conFiltroHombre.getWidgets().get(0);
        assertThat(widgetHombre.getEstado()).isEqualTo("OK");
        assertThat(widgetHombre.getResultadoActual().getValor()).isEqualTo(2.0);

        DashboardPanelRequestDto filtroMujer = new DashboardPanelRequestDto();
        filtroMujer.setFiltros(List.of(filtro("sexo", OperadorFiltro.EQ, "MUJER")));
        DashboardPanelResponseDto conFiltroMujer = dashboardPanelService.obtenerDashboard(panelId, filtroMujer);
        assertThat(conFiltroMujer.getWidgets().get(0).getResultadoActual().getValor()).isEqualTo(1.0);

        assertThat(conFiltroMujer.getFiltrosAplicados().getFiltros())
                .singleElement()
                .satisfies(f -> assertThat(f.getCampo()).isEqualTo("sexo"));
    }

    @Test
    void filtroGlobalPorProcedimiento_combinadoConOtroCampo_devuelveCeroSiNoHayCoincidencias() {
        Long datasetId = crearDatasetActivo("PROC");
        crearCampoComun(datasetId, "sexo", TipoDatoExcel.TEXTO);
        crearCampoComun(datasetId, "procedimiento", TipoDatoExcel.TEXTO);
        crearCampoComun(datasetId, "fechaEvento", TipoDatoExcel.FECHA);

        crearRegistro(datasetId, "P1", "HOMBRE", "COLECISTECTOMIA", LocalDate.of(2026, 1, 10));
        crearRegistro(datasetId, "P2", "MUJER", "APENDICECTOMIA", LocalDate.of(2026, 1, 11));

        Long metricaId = crearMetricaConteo(datasetId, codigoUnico("CONTEO"));
        Long panelId = crearPanel(datasetId, codigoUnico("PANEL"));
        anadirWidgetKpi(panelId, metricaId);

        DashboardPanelRequestDto filtroSinCoincidencias = new DashboardPanelRequestDto();
        filtroSinCoincidencias.setFiltros(List.of(
                filtro("sexo", OperadorFiltro.EQ, "MUJER"),
                filtro("procedimiento", OperadorFiltro.EQ, "COLECISTECTOMIA")));

        DashboardPanelResponseDto resultado = dashboardPanelService.obtenerDashboard(panelId, filtroSinCoincidencias);
        assertThat(resultado.getWidgets().get(0).getResultadoActual().getValor()).isEqualTo(0.0);
    }

    @Test
    void listarValoresUnicos_devuelveValoresDistintosOrdenadosDelCampo() {
        Long datasetId = crearDatasetActivo("VALORES");
        crearCampoComun(datasetId, "sexo", TipoDatoExcel.TEXTO);
        crearCampoComun(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO);

        crearRegistro(datasetId, "P1", "MUJER", null, null);
        crearRegistro(datasetId, "P2", "HOMBRE", null, null);
        crearRegistro(datasetId, "P3", "HOMBRE", null, null);

        List<String> valoresSexo = registroClinicoGenericoService.listarValoresUnicos(datasetId, "sexo");
        assertThat(valoresSexo).containsExactly("HOMBRE", "MUJER");

        List<String> pacientes = registroClinicoGenericoService.listarValoresUnicos(datasetId, "pacienteCodigo");
        assertThat(pacientes).containsExactly("P1", "P2", "P3");
    }

    @Test
    void listarValoresUnicos_campoInexistente_lanzaError() {
        Long datasetId = crearDatasetActivo("CAMPOFALTA");

        assertThatThrownBy(() -> registroClinicoGenericoService.listarValoresUnicos(datasetId, "no_existe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FiltroMetricaDto filtro(String campo, OperadorFiltro operador, Object valor) {
        FiltroMetricaDto dto = new FiltroMetricaDto();
        dto.setCampo(campo);
        dto.setOperador(operador);
        dto.setValor(valor);
        return dto;
    }
}
