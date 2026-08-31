package com.preventiva.backend;

import com.preventiva.backend.dto.BaseEvaluableDashboardDto;
import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.BaseEvaluableDashboardService;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Fase 6.9L.2 — sobre qué población cuentan los indicadores de actividad del
 * dashboard inicial.
 *
 * <p>El escenario es el del Excel real: 55 filas con contenido, 46
 * intervenciones y 9 fichas con solo el número de historia. Las 55 se conservan
 * —esa decisión no se toca—, pero el dashboard tiene que abrir diciendo 46
 * intervenciones, 46 pacientes y 100 % de completitud, no 55/55/84 %.
 *
 * <p>La regla vive en el dashboard inicial, no en el motor: un conteo escrito a
 * mano debe seguir devolviendo 55.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BaseEvaluableDashboardIntegrationTest {

    private static final String PREFIJO = "TEST_69L2_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private MetricaClinicaService metricaClinicaService;
    @Autowired private BaseEvaluableDashboardService baseEvaluableDashboardService;
    @Autowired private RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    private static final String C_ILQ = "infeccionLocalizacionQuirurgica";
    private static final String C_LOCALIZACION = "localizacionInfeccion";

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre("Test 69L2");
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

    /** Intervención completa: paciente, fecha y datos clínicos. */
    private void intervencion(Long datasetId, String paciente, LocalDate fecha, String ilq, String localizacion) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setFechaEvento(fecha);
        Map<String, Object> d = new HashMap<>();
        d.put(C_ILQ, ilq);
        if (localizacion != null) d.put(C_LOCALIZACION, localizacion);
        r.setDatosDinamicos(d);
        registroClinicoGenericoService.crear(r);
    }

    /** Ficha a medio rellenar: solo el número de historia, como en el archivo real. */
    private void fichaSoloPaciente(Long datasetId, String paciente) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setDatosDinamicos(Map.of());
        registroClinicoGenericoService.crear(r);
    }

    /**
     * Reproduce el archivo real: 46 intervenciones (3 con ILQ) y 9 fichas
     * vacías, con 55 pacientes todos distintos.
     */
    private Long datasetComoElArchivoReal() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, C_LOCALIZACION, TipoDatoExcel.TEXTO, false);

        for (int i = 1; i <= 43; i++) {
            intervencion(datasetId, "OP" + i, LocalDate.of(2026, 1, 1 + (i % 25)), "NO", null);
        }
        intervencion(datasetId, "OP44", LocalDate.of(2026, 2, 10), "SI", "ÓRGANO/ESPACIO");
        intervencion(datasetId, "OP45", LocalDate.of(2026, 2, 11), "SI", "ÓRGANO/ESPACIO");
        intervencion(datasetId, "OP46", LocalDate.of(2026, 3, 12), "SI", "PROFUNDA");

        for (int i = 1; i <= 9; i++) {
            fichaSoloPaciente(datasetId, "PLACEHOLDER" + i);
        }
        return datasetId;
    }

    /** Marca un campo como EXCLUIR, que es una preferencia de visualización. */
    private void excluirDelDashboard(Long datasetId, String codigo) {
        var campo = campoClinicoService.listarPorDataset(datasetId).stream()
                .filter(c -> codigo.equals(c.getCodigo()))
                .findFirst()
                .orElseThrow();
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(campo.getCodigo());
        r.setEtiqueta(campo.getEtiqueta());
        r.setTipoDato(TipoDatoExcel.valueOf(campo.getTipoDato()));
        r.setEsComun(Boolean.TRUE.equals(campo.getEsComun()));
        r.setObligatorio(false);
        r.setPrioridadDashboard(com.preventiva.backend.enums.PrioridadDashboardCampo.EXCLUIR);
        campoClinicoService.actualizar(datasetId, campo.getId(), r);
    }

    /** Ejecuta una métrica ad hoc, como haría el dashboard tras crearla. */
    private ResultadoMetricaResponseDto ejecutar(
            Long datasetId, String codigo, TipoMetrica tipo, ConfiguracionMetricaDto config) {
        MetricaClinicaRequestDto r = new MetricaClinicaRequestDto();
        r.setCodigo(codigo + "_" + contador.incrementAndGet());
        r.setNombre(codigo);
        r.setTipoMetrica(tipo);
        r.setConfiguracion(config);
        return metricaClinicaService.ejecutar(metricaClinicaService.crear(datasetId, r).getId(), null);
    }

    /** Configuración con los filtros que el dashboard inicial aplicaría a la actividad. */
    private ConfiguracionMetricaDto conBase(BaseEvaluableDashboardDto base, String campoValor) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(base.getFiltros());
        c.setCampoValor(campoValor);
        return c;
    }

    // ------------------------------------------------------------------
    // A–C: los tres indicadores de actividad
    // ------------------------------------------------------------------

    /** A: el volumen cuenta intervenciones, no filas. */
    @Test
    void volumenDelDashboardInicial_cuenta46IntervencionesYNo55Filas() {
        Long datasetId = datasetComoElArchivoReal();
        BaseEvaluableDashboardDto base = baseEvaluableDashboardService.resolver(datasetId);

        assertThat(base.isAplicaFechaEvento()).isTrue();
        assertThat(base.getEtiquetaVolumen()).isEqualTo("Intervenciones");
        assertThat(base.getFiltros()).singleElement().satisfies(f -> {
            assertThat(f.getCampo()).isEqualTo("fechaEvento");
            assertThat(f.getOperador()).isEqualTo(OperadorFiltro.NOT_NULL);
        });

        ResultadoMetricaResponseDto volumen =
                ejecutar(datasetId, "volumen", TipoMetrica.CONTEO, conBase(base, null));

        assertThat(volumen.getValor()).isEqualTo(46.0);
    }

    /** B: los 9 pacientes que solo constan en una ficha vacía no se cuentan. */
    @Test
    void pacientesUnicosDelDashboardInicial_cuenta46YNo55() {
        Long datasetId = datasetComoElArchivoReal();
        BaseEvaluableDashboardDto base = baseEvaluableDashboardService.resolver(datasetId);

        ResultadoMetricaResponseDto pacientes = ejecutar(
                datasetId, "pacientes", TipoMetrica.CONTEO_DISTINTO, conBase(base, "pacienteCodigo"));

        assertThat(pacientes.getValor()).isEqualTo(46.0);
    }

    /**
     * C: el caso que más engaña. Las 46 intervenciones tienen el dato de
     * infección, así que la completitud es del 100 %; sobre las 55 filas
     * parecería un 83,6 % y sugeriría un problema de registro inexistente.
     */
    @Test
    void completitudDelDashboardInicial_es100PorCientoYNo46De55() {
        Long datasetId = datasetComoElArchivoReal();
        BaseEvaluableDashboardDto base = baseEvaluableDashboardService.resolver(datasetId);

        ResultadoMetricaResponseDto completitud = ejecutar(
                datasetId, "completitud", TipoMetrica.COMPLETITUD, conBase(base, C_ILQ));

        assertThat(completitud.getTotalNumerador()).isEqualTo(46);
        assertThat(completitud.getTotalDenominador()).as("la población evaluable, no las 55 filas").isEqualTo(46);
        assertThat(completitud.getValor()).isCloseTo(100.0, within(0.01));

        // Contraste: sin acotar, la misma completitud da 46/55.
        ConfiguracionMetricaDto sinBase = new ConfiguracionMetricaDto();
        sinBase.setFiltros(List.of());
        sinBase.setCampoValor(C_ILQ);
        ResultadoMetricaResponseDto sinAcotar =
                ejecutar(datasetId, "completitud_sin_base", TipoMetrica.COMPLETITUD, sinBase);
        assertThat(sinAcotar.getTotalDenominador()).isEqualTo(55);
        assertThat(sinAcotar.getValor()).isCloseTo(83.64, within(0.01));
    }

    // ------------------------------------------------------------------
    // D: nada se ha borrado
    // ------------------------------------------------------------------

    /** D: los 55 registros siguen en la base. La solución es analítica, no destructiva. */
    @Test
    void los55RegistrosSiguenPersistidos() {
        Long datasetId = datasetComoElArchivoReal();

        List<com.preventiva.backend.entity.RegistroClinicoGenerico> registros =
                registroClinicoGenericoRepository.findByDatasetId(datasetId);

        assertThat(registros).hasSize(55);
        assertThat(registros).filteredOn(r -> r.getFechaEvento() == null).hasSize(9);
        // Y siguen siendo consultables con su identificador: se podrán completar.
        assertThat(registros).filteredOn(r -> r.getFechaEvento() == null)
                .allSatisfy(r -> assertThat(r.getPacienteCodigo()).startsWith("PLACEHOLDER"));
    }

    // ------------------------------------------------------------------
    // E–G: los indicadores clínicos no se mueven
    // ------------------------------------------------------------------

    /** E: Casos de ILQ sigue siendo 3. */
    @Test
    void casosDeIlq_siguenSiendo3() {
        Long datasetId = datasetComoElArchivoReal();

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of(filtro(C_ILQ, OperadorFiltro.EQ, true)));
        assertThat(ejecutar(datasetId, "casos", TipoMetrica.CONTEO, c).getValor()).isEqualTo(3.0);
    }

    /** F: la tasa sigue siendo 3/46, con su propio denominador. */
    @Test
    void tasaDeIlq_sigueSiendo3De46() {
        Long datasetId = datasetComoElArchivoReal();

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        c.setNumerador(grupo(filtro(C_ILQ, OperadorFiltro.EQ, true)));
        c.setDenominador(grupo(filtro(C_ILQ, OperadorFiltro.NOT_NULL, null)));

        ResultadoMetricaResponseDto tasa = ejecutar(datasetId, "tasa", TipoMetrica.PORCENTAJE, c);

        assertThat(tasa.getTotalNumerador()).isEqualTo(3);
        assertThat(tasa.getTotalDenominador()).isEqualTo(46);
        assertThat(tasa.getValor()).isCloseTo(6.52, within(0.01));
    }

    /** G: la localización sigue sumando 3. */
    @Test
    void localizacion_sigueSumando3() {
        Long datasetId = datasetComoElArchivoReal();

        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setCampoAgrupacion(C_LOCALIZACION);
        c.setFiltros(List.of(
                filtro(C_ILQ, OperadorFiltro.EQ, true),
                filtro(C_LOCALIZACION, OperadorFiltro.NOT_NULL, null)));

        ResultadoMetricaResponseDto loc = ejecutar(datasetId, "localizacion", TipoMetrica.DISTRIBUCION, c);

        assertThat(loc.getItems().stream().mapToLong(i -> i.getValor().longValue()).sum()).isEqualTo(3);
        assertThat(loc.getItems()).extracting(i -> i.getEtiqueta())
                .containsExactlyInAnyOrder("ÓRGANO/ESPACIO", "PROFUNDA");
    }

    // ------------------------------------------------------------------
    // H: datasets sin fechaEvento
    // ------------------------------------------------------------------

    /** H: sin fecha de evento no hay forma de acotar, y no se inventa un filtro. */
    @Test
    void datasetSinFechaEvento_conservaElComportamientoDeSiempre() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "servicio", TipoDatoExcel.TEXTO, true);
        for (int i = 1; i <= 5; i++) {
            fichaSoloPaciente(datasetId, "P" + i);
        }

        BaseEvaluableDashboardDto base = baseEvaluableDashboardService.resolver(datasetId);

        assertThat(base.isAplicaFechaEvento()).isFalse();
        assertThat(base.getFiltros()).isEmpty();
        assertThat(base.getEtiquetaVolumen()).isEqualTo("Total de registros");
        assertThat(base.getMotivo()).isNotBlank();

        assertThat(ejecutar(datasetId, "volumen", TipoMetrica.CONTEO, conBase(base, null)).getValor())
                .isEqualTo(5.0);
    }

    /** Un campo llamado fechaEvento pero que no es una fecha no sirve para acotar. */
    @Test
    void fechaEventoQueNoEsDeTipoFecha_noActivaLaRegla() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.TEXTO, true);

        assertThat(baseEvaluableDashboardService.resolver(datasetId).isAplicaFechaEvento()).isFalse();
    }

    // ------------------------------------------------------------------
    // 6.9L.2.1 — semántica de fechaEvento
    // ------------------------------------------------------------------

    /**
     * A: {@code prioridadDashboard} dice qué quiere ver el usuario, no qué es un
     * campo. Excluir la fecha de la propuesta de widgets no la invalida para
     * saber qué filas son eventos reales, así que la base evaluable sigue
     * aplicándose.
     *
     * <p>Protege además la independencia que introdujo la Fase 6.9J: esa marca
     * no debe convertirse en una regla de calidad ni de semántica.
     */
    @Test
    void fechaEventoExcluidaDelDashboard_sigueDefiniendoLaPoblacionEvaluable() {
        Long datasetId = datasetComoElArchivoReal();
        excluirDelDashboard(datasetId, "fechaEvento");

        BaseEvaluableDashboardDto base = baseEvaluableDashboardService.resolver(datasetId);

        assertThat(base.isAplicaFechaEvento()).isTrue();
        assertThat(base.getFiltros()).singleElement().satisfies(f -> {
            assertThat(f.getCampo()).isEqualTo("fechaEvento");
            assertThat(f.getOperador()).isEqualTo(OperadorFiltro.NOT_NULL);
        });
        assertThat(ejecutar(datasetId, "volumen_excluido", TipoMetrica.CONTEO, conBase(base, null)).getValor())
                .isEqualTo(46.0);
    }

    /** B: un dataset que registra infección quirúrgica sí cuenta intervenciones. */
    @Test
    void datasetQuirurgico_llamaAlVolumenIntervenciones() {
        Long datasetId = datasetComoElArchivoReal();

        assertThat(baseEvaluableDashboardService.resolver(datasetId).getEtiquetaVolumen())
                .isEqualTo("Intervenciones");
    }

    /**
     * C: el mismo dataset sin el campo de infección quirúrgica no es quirúrgico.
     * Su fechaEvento puede ser la de una consulta o un ingreso, y llamarlo
     * «intervenciones» sería una etiqueta clínicamente falsa.
     */
    @Test
    void datasetGenericoConFechaEvento_noLoLlamaIntervenciones() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo(datasetId, "servicio", TipoDatoExcel.TEXTO, true);
        intervencion(datasetId, "C1", LocalDate.of(2026, 1, 5), null, null);
        fichaSoloPaciente(datasetId, "C2");

        BaseEvaluableDashboardDto base = baseEvaluableDashboardService.resolver(datasetId);

        assertThat(base.isAplicaFechaEvento()).as("la población sí se acota").isTrue();
        assertThat(base.getEtiquetaVolumen()).isNotEqualTo("Intervenciones");
        assertThat(base.getEtiquetaVolumen()).isEqualTo("Eventos");
        // Y el filtro funciona igual: 1 evento con fecha, no 2 filas.
        assertThat(ejecutar(datasetId, "volumen_generico", TipoMetrica.CONTEO, conBase(base, null)).getValor())
                .isEqualTo(1.0);
    }

    /** La etiqueta no sale del nombre del dataset, sino del campo canónico. */
    @Test
    void laEtiqueta_noSeDeduceDelNombreDelDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre("Cirugía y quirófano: intervenciones ILQ 2026");
        Long datasetId = datasetClinicoService.crear(r).getId();
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);

        assertThat(baseEvaluableDashboardService.resolver(datasetId).getEtiquetaVolumen())
                .as("el nombre promete cirugía, pero no hay campo que lo sostenga")
                .isEqualTo("Eventos");
    }

    /** D: sin fechaEvento, todo sigue como antes. */
    @Test
    void datasetSinFechaEvento_mantieneLaEtiquetaNeutraDeSiempre() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, C_ILQ, TipoDatoExcel.BOOLEANO, false);

        BaseEvaluableDashboardDto base = baseEvaluableDashboardService.resolver(datasetId);

        assertThat(base.isAplicaFechaEvento()).isFalse();
        assertThat(base.getFiltros()).isEmpty();
        assertThat(base.getEtiquetaVolumen())
                .as("ni «Intervenciones» ni «Eventos»: no se acota nada")
                .isEqualTo("Total de registros");
    }

    // ------------------------------------------------------------------
    // I–J: la regla es del dashboard inicial, no del motor
    // ------------------------------------------------------------------

    /**
     * J: lo más importante de esta fase. Quien escribe a mano un conteo sin
     * filtros espera el número de filas, y sigue obteniendo 55. La regla acota
     * cómo se compone el dashboard inicial, no cómo cuenta el motor.
     */
    @Test
    void conteoManualSinFiltros_sigueDevolviendo55() {
        Long datasetId = datasetComoElArchivoReal();

        ConfiguracionMetricaDto sinFiltros = new ConfiguracionMetricaDto();
        sinFiltros.setFiltros(List.of());

        assertThat(ejecutar(datasetId, "conteo_manual", TipoMetrica.CONTEO, sinFiltros).getValor())
                .isEqualTo(55.0);
    }

    /** I: crear métricas a mano sigue funcionando igual, con o sin filtros. */
    @Test
    void creacionManualDeMetricas_noSeVeAfectada() {
        Long datasetId = datasetComoElArchivoReal();

        ConfiguracionMetricaDto distintos = new ConfiguracionMetricaDto();
        distintos.setFiltros(List.of());
        distintos.setCampoValor("pacienteCodigo");
        assertThat(ejecutar(datasetId, "distintos_manual", TipoMetrica.CONTEO_DISTINTO, distintos).getValor())
                .as("a mano y sin filtros, los 55 pacientes")
                .isEqualTo(55.0);

        ConfiguracionMetricaDto completitud = new ConfiguracionMetricaDto();
        completitud.setFiltros(List.of());
        completitud.setCampoValor(C_ILQ);
        assertThat(ejecutar(datasetId, "completitud_manual", TipoMetrica.COMPLETITUD, completitud)
                .getTotalDenominador())
                .isEqualTo(55);
    }

    /** Resolver la base evaluable es una consulta: no crea ni cambia nada. */
    @Test
    void resolverLaBaseEvaluable_noModificaNada() {
        Long datasetId = datasetComoElArchivoReal();
        long antes = registroClinicoGenericoRepository.findByDatasetId(datasetId).size();

        baseEvaluableDashboardService.resolver(datasetId);
        baseEvaluableDashboardService.resolver(datasetId);

        assertThat(registroClinicoGenericoRepository.findByDatasetId(datasetId)).hasSize((int) antes);
        assertThat(metricaClinicaService.listarPorDataset(datasetId)).isEmpty();
    }

    // ------------------------------------------------------------------

    private com.preventiva.backend.dto.FiltroMetricaDto filtro(String campo, OperadorFiltro op, Object valor) {
        com.preventiva.backend.dto.FiltroMetricaDto f = new com.preventiva.backend.dto.FiltroMetricaDto();
        f.setCampo(campo);
        f.setOperador(op);
        f.setValor(valor);
        return f;
    }

    private com.preventiva.backend.dto.FiltroGrupoDto grupo(com.preventiva.backend.dto.FiltroMetricaDto... filtros) {
        com.preventiva.backend.dto.FiltroGrupoDto g = new com.preventiva.backend.dto.FiltroGrupoDto();
        g.setFiltros(List.of(filtros));
        return g;
    }
}
