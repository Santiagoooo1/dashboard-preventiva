package com.preventiva.backend;

import com.preventiva.backend.dto.BloqueInformeRequestDto;
import com.preventiva.backend.dto.BloqueInformeResponseDto;
import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CatalogoInformeResponseDto;
import com.preventiva.backend.dto.DashboardDisponibleDto;
import com.preventiva.backend.dto.DashboardPanelRequestDto;
import com.preventiva.backend.dto.IndicadorDisponibleDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.PanelMetricaRequestDto;
import com.preventiva.backend.dto.WidgetDisponibleDto;
import com.preventiva.backend.dto.ComparacionInteranualRequestDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.ConfiguracionWidgetDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.InformeClinicoRequestDto;
import com.preventiva.backend.dto.InformeClinicoResponseDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.PaginaInformeResponseDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoBloqueInforme;
import com.preventiva.backend.enums.TipoComparacionInteranual;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.entity.InformeClinico;
import com.preventiva.backend.entity.PanelMetrica;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.repository.BloqueInformeRepository;
import com.preventiva.backend.repository.InformeClinicoRepository;
import com.preventiva.backend.repository.PanelMetricaRepository;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.CatalogoInformeService;
import com.preventiva.backend.service.interfaces.DashboardPanelService;
import com.preventiva.backend.service.interfaces.PanelClinicoService;
import com.preventiva.backend.service.interfaces.PanelMetricaService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.InformeClinicoService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 6.9Q — el constructor de informes.
 *
 * <p>Lo que más se protege aquí es que un informe <b>referencie</b> y no copie.
 * Si guardara «tasa = 3,23 %», el documento envejecería en silencio: se abriría
 * meses después con la cifra del día en que se montó y nadie notaría la
 * diferencia hasta tomar una decisión con ella.
 *
 * <p>Y que una referencia rota no tumbe el documento entero: el bloque afectado
 * lo dice, el resto se sigue viendo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InformeClinicoIntegrationTest {

    private static final String PREFIJO = "TEST_69Q_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private InformeClinicoService informeService;
    @Autowired private DatasetClinicoService datasetService;
    @Autowired private CampoClinicoService campoService;
    @Autowired private MetricaClinicaService metricaService;
    @Autowired private RegistroClinicoGenericoService registroService;
    @Autowired private BloqueInformeRepository bloqueRepository;
    @Autowired private InformeClinicoRepository informeRepository;
    @Autowired private CatalogoInformeService catalogoService;
    @Autowired private PanelClinicoService panelService;
    @Autowired private PanelMetricaService panelMetricaService;
    @Autowired private PanelMetricaRepository panelMetricaRepository;
    @Autowired private DashboardPanelService dashboardPanelService;
    @PersistenceContext private EntityManager entityManager;

    private static final String C_ILQ = "infeccionLocalizacionQuirurgica";

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Como lo crea la interfaz desde 6.9Q.2: un solo título. */
    private InformeClinicoResponseDto crearInforme(String titulo) {
        InformeClinicoRequestDto r = new InformeClinicoRequestDto();
        r.setNombre(titulo);
        return informeService.crear(r);
    }

    private Long crearDataset(String nombre) {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre(nombre);
        Long id = datasetService.crear(r).getId();
        crearCampo(id, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(id, "fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo(id, C_ILQ, TipoDatoExcel.BOOLEANO, false);
        return id;
    }

    private void crearCampo(Long datasetId, String codigo, TipoDatoExcel tipo, boolean comun) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(codigo);
        r.setTipoDato(tipo);
        r.setEsComun(comun);
        r.setObligatorio(false);
        campoService.crear(datasetId, r);
    }

    private void registro(Long datasetId, LocalDate fecha, String ilq) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo("P" + contador.incrementAndGet());
        r.setFechaEvento(fecha);
        Map<String, Object> d = new HashMap<>();
        if (ilq != null) d.put(C_ILQ, ilq);
        r.setDatosDinamicos(d);
        registroService.crear(r);
    }

    /** Tasa de ILQ: el indicador que se usa en casi todos los informes reales. */
    private Long crearMetricaTasa(Long datasetId) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setFiltros(List.of());
        c.setNumerador(grupo(filtro(C_ILQ, OperadorFiltro.EQ, true)));
        c.setDenominador(grupo(filtro(C_ILQ, OperadorFiltro.NOT_NULL, null)));

        MetricaClinicaRequestDto r = new MetricaClinicaRequestDto();
        r.setCodigo("tasa_" + contador.incrementAndGet());
        r.setNombre("Tasa de ILQ");
        r.setTipoMetrica(TipoMetrica.PORCENTAJE);
        r.setConfiguracion(c);
        r.setUnidad("%");
        r.setDecimales(2);
        return metricaService.crear(datasetId, r).getId();
    }

    private Long crearPanel(Long datasetId, String nombre) {
        PanelClinicoRequestDto r = new PanelClinicoRequestDto();
        r.setCodigo("panel_" + contador.incrementAndGet());
        r.setNombre(nombre);
        r.setOrden(0);
        return panelService.crear(datasetId, r).getId();
    }

    /** Un widget tal como lo tendría el usuario en su dashboard. */
    private Long anadirWidget(
            Long panelId, Long metricaId, TipoVisualizacion visualizacion,
            TipoResultadoWidget tipoResultado, Granularidad granularidad, int ancho) {
        PanelMetricaRequestDto r = new PanelMetricaRequestDto();
        r.setMetricaId(metricaId);
        r.setTipoVisualizacion(visualizacion);
        r.setAncho(ancho);
        r.setOrden(0);
        Long id = panelMetricaService.crear(panelId, r).getId();

        if (tipoResultado != null) {
            PanelMetrica pm = panelMetricaRepository.findById(id).orElseThrow();
            pm.setTipoResultadoWidget(tipoResultado);
            if (granularidad != null) {
                ConfiguracionWidgetDto config = new ConfiguracionWidgetDto();
                config.setGranularidad(granularidad);
                pm.setConfiguracionWidget(config);
            }
            panelMetricaRepository.save(pm);
        }
        return id;
    }

    private BloqueInformeRequestDto bloque(TipoBloqueInforme tipo) {
        BloqueInformeRequestDto r = new BloqueInformeRequestDto();
        r.setTipoBloque(tipo);
        return r;
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

    private Long primeraPagina(InformeClinicoResponseDto informe) {
        return informe.getPaginas().get(0).getId();
    }

    // ------------------------------------------------------------------
    // A–B: informe y páginas
    // ------------------------------------------------------------------

    /** A y N: un informe nuevo es válido y ya tiene una hoja donde empezar. */
    @Test
    void crearInforme_naceConUnaPaginaVaciaYEsValido() {
        InformeClinicoResponseDto informe = crearInforme("Informe de vigilancia");

        assertThat(informe.getId()).isNotNull();
        assertThat(informe.getNombre()).isEqualTo("Informe de vigilancia");
        assertThat(informe.getPaginas()).hasSize(1);
        assertThat(informe.getPaginas().get(0).getBloques()).isEmpty();
        assertThat(informe.getPaginas().get(0).getOrientacion()).isEqualTo("VERTICAL");

        // Un informe sin bloques se carga sin errores.
        assertThat(informeService.obtenerConResultados(informe.getId()).getPaginas()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Fase 6.9Q.2: un único título visible
    // ------------------------------------------------------------------

    /**
     * A, B y C: crear un informe deja un solo título, y es el mismo en el
     * listado y en la estructura.
     *
     * <p>`nombre` y `titulo` significaban casi lo mismo y la interfaz pedía los
     * dos. Las columnas siguen existiendo —quitarlas obligaría a una migración
     * que esta fase no necesita— pero ya nunca divergen.
     */
    @Test
    void crearInforme_dejaUnUnicoTituloEnTodasPartes() {
        InformeClinicoResponseDto informe = crearInforme("Informe de vigilancia ILQ 2026");

        assertThat(informe.getTituloVisible()).isEqualTo("Informe de vigilancia ILQ 2026");
        assertThat(informe.getNombre())
                .as("las dos columnas guardan lo mismo")
                .isEqualTo(informe.getTitulo());

        InformeClinicoResponseDto delListado = informeService.listar().stream()
                .filter(i -> i.getId().equals(informe.getId())).findFirst().orElseThrow();
        assertThat(delListado.getTituloVisible()).isEqualTo("Informe de vigilancia ILQ 2026");

        assertThat(informeService.obtenerEstructura(informe.getId()).getTituloVisible())
                .isEqualTo("Informe de vigilancia ILQ 2026");
        assertThat(informeService.obtenerConResultados(informe.getId()).getTituloVisible())
                .isEqualTo("Informe de vigilancia ILQ 2026");
    }

    /**
     * B: guardar no puede dejar los dos campos distintos.
     *
     * <p>Aunque la petición traiga nombre «Informe A» y título «Informe B»
     * —un cliente antiguo, una llamada a mano—, se guarda uno solo. Si no, la
     * ambigüedad que quitamos de la pantalla volvería por la puerta de atrás.
     */
    @Test
    void guardar_unificaAunqueLaPeticionTraigaDosTextosDistintos() {
        InformeClinicoResponseDto informe = crearInforme("Informe inicial");

        InformeClinicoRequestDto r = new InformeClinicoRequestDto();
        r.setNombre("Informe A");
        r.setTitulo("Informe B");
        InformeClinicoResponseDto guardado = informeService.actualizar(informe.getId(), r);

        assertThat(guardado.getNombre()).isEqualTo(guardado.getTitulo());
        assertThat(guardado.getTituloVisible())
                .as("gana el titulo, que es lo que la pantalla enseña")
                .isEqualTo("Informe B");
    }

    /**
     * G: los informes creados antes de 6.9Q.2 siguen abriéndose.
     *
     * <p>Pueden traer los dos campos distintos o el título vacío. La prioridad
     * es estable —título si tiene contenido, si no nombre— y leerlos no corrige
     * nada: la unificación ocurre al guardar, no al abrir.
     */
    @Test
    void informesAnteriores_sigueCargandoConPrioridadEstable() {
        InformeClinico divergente = informeRepository.save(InformeClinico.builder()
                .nombre("Nombre antiguo").titulo("Título antiguo")
                .creadoEn(LocalDateTime.now()).actualizadoEn(LocalDateTime.now())
                .activo(true).build());
        InformeClinico sinTitulo = informeRepository.save(InformeClinico.builder()
                .nombre("Solo nombre").titulo(null)
                .creadoEn(LocalDateTime.now()).actualizadoEn(LocalDateTime.now())
                .activo(true).build());
        InformeClinico tituloEnBlanco = informeRepository.save(InformeClinico.builder()
                .nombre("Nombre de respaldo").titulo("   ")
                .creadoEn(LocalDateTime.now()).actualizadoEn(LocalDateTime.now())
                .activo(true).build());

        assertThat(informeService.obtenerEstructura(divergente.getId()).getTituloVisible())
                .isEqualTo("Título antiguo");
        assertThat(informeService.obtenerEstructura(sinTitulo.getId()).getTituloVisible())
                .isEqualTo("Solo nombre");
        assertThat(informeService.obtenerEstructura(tituloEnBlanco.getId()).getTituloVisible())
                .as("un titulo en blanco no es un titulo")
                .isEqualTo("Nombre de respaldo");

        // Y abrir uno divergente no lo toca: solo guardar unifica.
        InformeClinico enBase = informeRepository.findById(divergente.getId()).orElseThrow();
        assertThat(enBase.getNombre()).isEqualTo("Nombre antiguo");
    }

    /**
     * F: un bloque TITULO es contenido del documento, no el título general.
     *
     * <p>Son cosas distintas y deben poder decir cosas distintas: «Informe de
     * vigilancia ILQ 2026» encabeza la hoja y «Resultados del primer semestre»
     * es una sección dentro.
     */
    @Test
    void bloqueTitulo_esIndependienteDelTituloDelInforme() {
        InformeClinicoResponseDto informe = crearInforme("Informe de vigilancia ILQ 2026");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.TITULO);
        peticion.setContenidoTexto("Resultados del primer semestre");
        informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        InformeClinicoResponseDto recargado = informeService.obtenerEstructura(informe.getId());
        assertThat(recargado.getTituloVisible()).isEqualTo("Informe de vigilancia ILQ 2026");
        assertThat(recargado.getPaginas().get(0).getBloques())
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.getTipoBloque()).isEqualTo("TITULO");
                    assertThat(b.getContenidoTexto()).isEqualTo("Resultados del primer semestre");
                });
    }

    /** B e I: añadir páginas y conservar su orden. */
    @Test
    void anadirPaginas_lasNumeraEnOrden() {
        InformeClinicoResponseDto informe = crearInforme("Informe con varias páginas");
        informeService.anadirPagina(informe.getId());
        informeService.anadirPagina(informe.getId());

        InformeClinicoResponseDto recargado = informeService.obtenerEstructura(informe.getId());

        assertThat(recargado.getPaginas()).hasSize(3);
        assertThat(recargado.getPaginas()).extracting(PaginaInformeResponseDto::getOrden)
                .containsExactly(0, 1, 2);
        assertThat(recargado.getTotalPaginas()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // C–F: los tipos de bloque
    // ------------------------------------------------------------------

    /** C: un KPI referencia una métrica; no guarda su valor. */
    @Test
    void anadirKpi_referenciaLaMetricaYNoCopiaSuValor() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO");

        InformeClinicoResponseDto informe = crearInforme("Informe con KPI");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.KPI);
        peticion.setMetricaId(metricaId);

        BloqueInformeResponseDto creado =
                informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        assertThat(creado.getMetricaId()).isEqualTo(metricaId);
        assertThat(creado.getTipoVisualizacion()).isEqualTo("KPI");
        assertThat(creado.getAncho()).as("un KPI cabe cuatro por fila").isEqualTo(3);
        assertThat(creado.isDisponible()).isTrue();

        // Lo guardado es la referencia; el valor aparece al pedir resultados.
        assertThat(bloqueRepository.findById(creado.getId()).orElseThrow().getMetrica().getId())
                .isEqualTo(metricaId);

        InformeClinicoResponseDto conDatos = informeService.obtenerConResultados(informe.getId());
        BloqueInformeResponseDto resuelto = conDatos.getPaginas().get(0).getBloques().get(0);
        assertThat(resuelto.getWidget()).isNotNull();
        assertThat(resuelto.getWidget().getResultadoActual().getValor()).isEqualTo(50.0);
    }

    /** El resultado se recalcula: cambian los datos, cambia el informe. */
    @Test
    void elInforme_muestraLosDatosDeHoyYNoLosDelDiaEnQueSeMonto() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO");

        InformeClinicoResponseDto informe = crearInforme("Informe dinámico");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.KPI);
        peticion.setMetricaId(metricaId);
        informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        assertThat(valorDelPrimerBloque(informe.getId())).isEqualTo(50.0);

        // Llegan dos intervenciones más sin infección: 1 de 4.
        registro(datasetId, LocalDate.of(2026, 2, 5), "NO");
        registro(datasetId, LocalDate.of(2026, 2, 6), "NO");

        assertThat(valorDelPrimerBloque(informe.getId()))
                .as("el informe refleja los datos actuales, no una copia")
                .isEqualTo(25.0);
    }

    private Double valorDelPrimerBloque(Long informeId) {
        return informeService.obtenerConResultados(informeId)
                .getPaginas().get(0).getBloques().get(0)
                .getWidget().getResultadoActual().getValor();
    }

    /** D: una gráfica con su serie temporal. */
    @Test
    void anadirGrafica_conSerieTemporal() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");
        registro(datasetId, LocalDate.of(2026, 2, 6), "NO");

        InformeClinicoResponseDto informe = crearInforme("Informe con gráfica");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.GRAFICA);
        peticion.setMetricaId(metricaId);
        peticion.setTipoVisualizacion(TipoVisualizacion.LINEAS);
        peticion.setTipoResultadoWidget(TipoResultadoWidget.SERIE_TEMPORAL);
        ConfiguracionWidgetDto config = new ConfiguracionWidgetDto();
        config.setGranularidad(Granularidad.MES);
        peticion.setConfiguracionWidget(config);

        BloqueInformeResponseDto creado =
                informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        assertThat(creado.getAncho()).as("una gráfica ocupa la fila entera").isEqualTo(12);

        BloqueInformeResponseDto resuelto = informeService.obtenerConResultados(informe.getId())
                .getPaginas().get(0).getBloques().get(0);
        assertThat(resuelto.getWidget().getSerieTemporal()).isNotNull();
        assertThat(resuelto.getWidget().getSerieTemporal().getPuntos()).hasSize(2);
    }

    /** E: una tabla temporal, con el mismo motor que en el dashboard. */
    @Test
    void anadirTablaTemporal() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO");

        InformeClinicoResponseDto informe = crearInforme("Informe con tabla");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.TABLA);
        peticion.setMetricaId(metricaId);
        peticion.setTipoVisualizacion(TipoVisualizacion.TABLA);
        peticion.setTipoResultadoWidget(TipoResultadoWidget.SERIE_TEMPORAL);
        ConfiguracionWidgetDto config = new ConfiguracionWidgetDto();
        config.setGranularidad(Granularidad.MES);
        peticion.setConfiguracionWidget(config);
        informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        BloqueInformeResponseDto resuelto = informeService.obtenerConResultados(informe.getId())
                .getPaginas().get(0).getBloques().get(0);

        assertThat(resuelto.getWidget().getSerieTemporal().getTotal().getValor()).isEqualTo(50.0);
    }

    /** F: texto plano, sin HTML. */
    @Test
    void anadirTextoYTitulo() {
        InformeClinicoResponseDto informe = crearInforme("Informe con texto");
        Long pagina = primeraPagina(informe);

        BloqueInformeRequestDto titulo = bloque(TipoBloqueInforme.TITULO);
        titulo.setContenidoTexto("Vigilancia de ILQ");
        BloqueInformeRequestDto texto = bloque(TipoBloqueInforme.TEXTO);
        texto.setContenidoTexto("Durante 2026 se vigilaron las intervenciones de trauma.\nSegunda línea.");

        informeService.anadirBloque(informe.getId(), pagina, titulo);
        informeService.anadirBloque(informe.getId(), pagina, texto);

        List<BloqueInformeResponseDto> bloques = informeService.obtenerEstructura(informe.getId())
                .getPaginas().get(0).getBloques();

        assertThat(bloques).hasSize(2);
        assertThat(bloques.get(0).getContenidoTexto()).isEqualTo("Vigilancia de ILQ");
        assertThat(bloques.get(1).getContenidoTexto()).contains("\n");
        assertThat(bloques).allSatisfy(b -> assertThat(b.getMetricaId()).isNull());
    }

    /** Un bloque de contenido sin texto no se guarda a medias. */
    @Test
    void bloqueDeTextoSinContenido_seRechaza() {
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);

        assertThatThrownBy(() ->
                informeService.anadirBloque(informe.getId(), pagina, bloque(TipoBloqueInforme.TEXTO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("texto");
    }

    /** Un KPI sin indicador tampoco. */
    @Test
    void bloqueAnaliticoSinMetrica_seRechaza() {
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);

        assertThatThrownBy(() ->
                informeService.anadirBloque(informe.getId(), pagina, bloque(TipoBloqueInforme.KPI)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indicador");
    }

    // ------------------------------------------------------------------
    // G–H: reordenar y redimensionar
    // ------------------------------------------------------------------

    /** G: mover un bloque renumera el resto. */
    @Test
    void moverBloque_reordenaLaPagina() {
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);

        for (String texto : List.of("Primero", "Segundo", "Tercero")) {
            BloqueInformeRequestDto b = bloque(TipoBloqueInforme.TEXTO);
            b.setContenidoTexto(texto);
            informeService.anadirBloque(informe.getId(), pagina, b);
        }
        List<BloqueInformeResponseDto> antes =
                informeService.obtenerEstructura(informe.getId()).getPaginas().get(0).getBloques();
        Long tercero = antes.get(2).getId();

        PaginaInformeResponseDto resultado =
                informeService.moverBloque(informe.getId(), pagina, tercero, 0);

        assertThat(resultado.getBloques()).extracting(BloqueInformeResponseDto::getContenidoTexto)
                .containsExactly("Tercero", "Primero", "Segundo");
        assertThat(resultado.getBloques()).extracting(BloqueInformeResponseDto::getOrden)
                .containsExactly(0, 1, 2);
    }

    /** H: cambiar el ancho, acotado a la rejilla de 12. */
    @Test
    void cambiarAnchoDeUnBloque() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);

        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.KPI);
        peticion.setMetricaId(metricaId);
        BloqueInformeResponseDto creado = informeService.anadirBloque(informe.getId(), pagina, peticion);

        peticion.setAncho(6);
        BloqueInformeResponseDto actualizado =
                informeService.actualizarBloque(informe.getId(), pagina, creado.getId(), peticion);
        assertThat(actualizado.getAncho()).isEqualTo(6);

        // Fuera de rango se recorta en vez de romper la rejilla.
        peticion.setAncho(99);
        assertThat(informeService.actualizarBloque(informe.getId(), pagina, creado.getId(), peticion)
                .getAncho()).isEqualTo(12);
    }

    // ------------------------------------------------------------------
    // J–L: borrar y persistencia
    // ------------------------------------------------------------------

    /** J: eliminar un bloque renumera los que quedan. */
    @Test
    void eliminarBloque_renumeraLosRestantes() {
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);
        for (String texto : List.of("A", "B", "C")) {
            BloqueInformeRequestDto b = bloque(TipoBloqueInforme.TEXTO);
            b.setContenidoTexto(texto);
            informeService.anadirBloque(informe.getId(), pagina, b);
        }
        Long segundo = informeService.obtenerEstructura(informe.getId())
                .getPaginas().get(0).getBloques().get(1).getId();

        informeService.eliminarBloque(informe.getId(), pagina, segundo);

        List<BloqueInformeResponseDto> quedan =
                informeService.obtenerEstructura(informe.getId()).getPaginas().get(0).getBloques();
        assertThat(quedan).extracting(BloqueInformeResponseDto::getContenidoTexto).containsExactly("A", "C");
        assertThat(quedan).extracting(BloqueInformeResponseDto::getOrden).containsExactly(0, 1);
    }

    /** K: eliminar una página se lleva sus bloques, pero nunca la última. */
    @Test
    void eliminarPagina_seLlevaSusBloquesYNuncaDejaElInformeSinHojas() {
        InformeClinicoResponseDto informe = crearInforme("Informe");
        PaginaInformeResponseDto segunda = informeService.anadirPagina(informe.getId());

        BloqueInformeRequestDto b = bloque(TipoBloqueInforme.TEXTO);
        b.setContenidoTexto("En la segunda página");
        BloqueInformeResponseDto creado =
                informeService.anadirBloque(informe.getId(), segunda.getId(), b);

        informeService.eliminarPagina(informe.getId(), segunda.getId());

        assertThat(informeService.obtenerEstructura(informe.getId()).getPaginas()).hasSize(1);
        assertThat(bloqueRepository.findById(creado.getId())).isEmpty();

        // La última no se puede borrar: un informe sin hojas no es editable.
        Long ultima = primeraPagina(informeService.obtenerEstructura(informe.getId()));
        assertThatThrownBy(() -> informeService.eliminarPagina(informe.getId(), ultima))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos una página");
    }

    /** L: recargar el informe conserva el orden de páginas y bloques. */
    @Test
    void recargarElInforme_conservaTodoElOrden() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        InformeClinicoResponseDto informe = crearInforme("Informe completo");
        Long pagina1 = primeraPagina(informe);
        PaginaInformeResponseDto pagina2 = informeService.anadirPagina(informe.getId());

        BloqueInformeRequestDto titulo = bloque(TipoBloqueInforme.TITULO);
        titulo.setContenidoTexto("Portada");
        informeService.anadirBloque(informe.getId(), pagina1, titulo);

        BloqueInformeRequestDto kpi = bloque(TipoBloqueInforme.KPI);
        kpi.setMetricaId(metricaId);
        informeService.anadirBloque(informe.getId(), pagina1, kpi);

        BloqueInformeRequestDto texto = bloque(TipoBloqueInforme.TEXTO);
        texto.setContenidoTexto("Conclusiones");
        informeService.anadirBloque(informe.getId(), pagina2.getId(), texto);

        InformeClinicoResponseDto recargado = informeService.obtenerEstructura(informe.getId());

        assertThat(recargado.getPaginas()).hasSize(2);
        assertThat(recargado.getPaginas().get(0).getBloques())
                .extracting(BloqueInformeResponseDto::getTipoBloque)
                .containsExactly("TITULO", "KPI");
        assertThat(recargado.getPaginas().get(1).getBloques())
                .extracting(BloqueInformeResponseDto::getContenidoTexto)
                .containsExactly("Conclusiones");
    }

    /** Duplicar una página copia las referencias, no los resultados. */
    @Test
    void duplicarPagina_copiaLasReferencias() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);

        BloqueInformeRequestDto kpi = bloque(TipoBloqueInforme.KPI);
        kpi.setMetricaId(metricaId);
        informeService.anadirBloque(informe.getId(), pagina, kpi);

        PaginaInformeResponseDto copia = informeService.duplicarPagina(informe.getId(), pagina);

        assertThat(copia.getBloques()).hasSize(1);
        assertThat(copia.getBloques().get(0).getMetricaId()).isEqualTo(metricaId);
        assertThat(copia.getBloques().get(0).getId()).isNotEqualTo(pagina);
        assertThat(informeService.obtenerEstructura(informe.getId()).getPaginas()).hasSize(2);
    }

    // ------------------------------------------------------------------
    // M: referencias rotas
    // ------------------------------------------------------------------

    /**
     * M: si la métrica desaparece, el bloque lo dice y el resto del informe se
     * sigue viendo. Nunca un 500 ni una página en blanco.
     */
    @Test
    void metricaBorrada_dejaElBloqueMarcadoSinTumbarElInforme() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");

        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);

        BloqueInformeRequestDto kpi = bloque(TipoBloqueInforme.KPI);
        kpi.setMetricaId(metricaId);
        BloqueInformeResponseDto creado = informeService.anadirBloque(informe.getId(), pagina, kpi);

        BloqueInformeRequestDto texto = bloque(TipoBloqueInforme.TEXTO);
        texto.setContenidoTexto("Este texto debe seguir viéndose");
        informeService.anadirBloque(informe.getId(), pagina, texto);

        // La métrica desaparece por debajo del informe.
        bloqueRepository.findById(creado.getId()).ifPresent(b -> {
            b.setMetrica(null);
            bloqueRepository.save(b);
        });

        InformeClinicoResponseDto conDatos = informeService.obtenerConResultados(informe.getId());
        List<BloqueInformeResponseDto> bloques = conDatos.getPaginas().get(0).getBloques();

        assertThat(bloques).hasSize(2);
        assertThat(bloques.get(0).isDisponible()).isFalse();
        assertThat(bloques.get(0).getMotivoNoDisponible()).contains("ya no está disponible");
        assertThat(bloques.get(1).getContenidoTexto()).isEqualTo("Este texto debe seguir viéndose");
    }

    /** Un bloque roto se puede quitar sin más. */
    @Test
    void bloqueNoDisponible_sePuedeEliminar() {
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);
        Long datasetId = crearDataset("ILQ 2026");
        BloqueInformeRequestDto kpi = bloque(TipoBloqueInforme.KPI);
        kpi.setMetricaId(crearMetricaTasa(datasetId));
        BloqueInformeResponseDto creado = informeService.anadirBloque(informe.getId(), pagina, kpi);

        bloqueRepository.findById(creado.getId()).ifPresent(b -> {
            b.setMetrica(null);
            bloqueRepository.save(b);
        });

        informeService.eliminarBloque(informe.getId(), pagina, creado.getId());
        assertThat(informeService.obtenerEstructura(informe.getId())
                .getPaginas().get(0).getBloques()).isEmpty();
    }

    // ------------------------------------------------------------------
    // O–P: comparación y varios datasets
    // ------------------------------------------------------------------

    /** O: una comparación interanual dentro del informe, con su propio motor. */
    @Test
    void anadirComparacionInteranual() {
        Long d2025 = crearDataset("ILQ 2025");
        registro(d2025, LocalDate.of(2025, 1, 5), "SI");
        registro(d2025, LocalDate.of(2025, 1, 6), "NO");
        Long d2026 = crearDataset("ILQ 2026");
        registro(d2026, LocalDate.of(2026, 1, 5), "NO");
        registro(d2026, LocalDate.of(2026, 1, 6), "NO");

        InformeClinicoResponseDto informe = crearInforme("Informe interanual");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.COMPARACION_INTERANUAL);
        ComparacionInteranualRequestDto config = new ComparacionInteranualRequestDto();
        config.setDatasetIds(List.of(d2025, d2026));
        config.setCodigoCanonico(C_ILQ);
        config.setTipoComparacion(TipoComparacionInteranual.TASA);
        config.setGranularidad(Granularidad.MES);
        peticion.setConfiguracionComparacion(config);

        informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        BloqueInformeResponseDto resuelto = informeService.obtenerConResultados(informe.getId())
                .getPaginas().get(0).getBloques().get(0);

        assertThat(resuelto.getComparacion()).isNotNull();
        assertThat(resuelto.getComparacion().isComparable()).isTrue();
        assertThat(resuelto.getComparacion().getSeries()).hasSize(2);
        assertThat(resuelto.getComparacion().getSeries().get(0).getTotal().getValor()).isEqualTo(50.0);
    }

    /** Una comparación sin datasets no se guarda. */
    @Test
    void comparacionSinDatasets_seRechaza() {
        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);

        assertThatThrownBy(() -> informeService.anadirBloque(
                informe.getId(), pagina, bloque(TipoBloqueInforme.COMPARACION_INTERANUAL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataset");
    }

    /** P: un informe puede mezclar contenido de varios datasets. */
    @Test
    void unInforme_puedeUsarVariosDatasets() {
        Long ilq = crearDataset("ILQ 2026");
        Long cesareas = crearDataset("Cesáreas 2026");
        registro(ilq, LocalDate.of(2026, 1, 5), "SI");
        registro(cesareas, LocalDate.of(2026, 1, 5), "NO");

        InformeClinicoResponseDto informe = crearInforme("Informe multiservicio");
        Long pagina1 = primeraPagina(informe);
        PaginaInformeResponseDto pagina2 = informeService.anadirPagina(informe.getId());

        BloqueInformeRequestDto kpiIlq = bloque(TipoBloqueInforme.KPI);
        kpiIlq.setMetricaId(crearMetricaTasa(ilq));
        informeService.anadirBloque(informe.getId(), pagina1, kpiIlq);

        BloqueInformeRequestDto kpiCesareas = bloque(TipoBloqueInforme.KPI);
        kpiCesareas.setMetricaId(crearMetricaTasa(cesareas));
        informeService.anadirBloque(informe.getId(), pagina2.getId(), kpiCesareas);

        InformeClinicoResponseDto conDatos = informeService.obtenerConResultados(informe.getId());

        assertThat(conDatos.getPaginas().get(0).getBloques().get(0).getDatasetNombre())
                .isEqualTo("ILQ 2026");
        assertThat(conDatos.getPaginas().get(1).getBloques().get(0).getDatasetNombre())
                .isEqualTo("Cesáreas 2026");
        // Trazabilidad para el pie del documento, sin ids técnicos.
        assertThat(conDatos.getDatasetsUtilizados())
                .containsExactlyInAnyOrder("ILQ 2026", "Cesáreas 2026");
        assertThat(conDatos.getGeneradoEn()).isNotNull();
    }

    /** Dos bloques del mismo indicador comparten cálculo, no lo repiten. */
    @Test
    void bloquesQueRepitenIndicador_seCalculanUnaSolaVez() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO");

        InformeClinicoResponseDto informe = crearInforme("Informe");
        Long pagina = primeraPagina(informe);
        for (int i = 0; i < 3; i++) {
            BloqueInformeRequestDto kpi = bloque(TipoBloqueInforme.KPI);
            kpi.setMetricaId(metricaId);
            informeService.anadirBloque(informe.getId(), pagina, kpi);
        }

        List<BloqueInformeResponseDto> bloques = informeService.obtenerConResultados(informe.getId())
                .getPaginas().get(0).getBloques();

        assertThat(bloques).hasSize(3);
        assertThat(bloques).allSatisfy(b ->
                assertThat(b.getWidget().getResultadoActual().getValor()).isEqualTo(50.0));
        // Misma instancia: la caché de la petición los ha unificado.
        assertThat(bloques.get(0).getWidget()).isSameAs(bloques.get(1).getWidget());
    }

    // ------------------------------------------------------------------
    // 6.9Q.1 — catálogo de widgets reales
    // ------------------------------------------------------------------

    /**
     * A–D: un widget insertado desde un dashboard conserva EXACTAMENTE su
     * configuración.
     *
     * <p>Antes la biblioteca partía de la métrica e inventaba la
     * representación, así que una evolución mensual —línea temporal con
     * granularidad de mes— aterrizaba en el informe como unas barras genéricas
     * que no se parecían a lo que el médico había aprobado.
     */
    @Test
    void elCatalogo_devuelveLosWidgetsConSuConfiguracionExacta() {
        Long datasetId = crearDataset("ILQ 2026");
        Long metricaId = crearMetricaTasa(datasetId);
        Long panelId = crearPanel(datasetId, "Dashboard ILQ 2026");

        anadirWidget(panelId, metricaId, TipoVisualizacion.KPI, null, null, 3);
        anadirWidget(panelId, crearMetricaTasa(datasetId), TipoVisualizacion.LINEAS,
                TipoResultadoWidget.SERIE_TEMPORAL, Granularidad.MES, 12);
        anadirWidget(panelId, crearMetricaTasa(datasetId), TipoVisualizacion.TABLA,
                TipoResultadoWidget.SERIE_TEMPORAL, Granularidad.MES, 12);

        CatalogoInformeResponseDto catalogo = catalogoService.obtener();
        DashboardDisponibleDto dashboard = catalogo.getDashboards().stream()
                .filter(d -> d.getPanelId().equals(panelId)).findFirst().orElseThrow();

        assertThat(dashboard.getDatasetNombre()).isEqualTo("ILQ 2026");
        assertThat(dashboard.getWidgets()).hasSize(3);

        // D: el KPI sigue siendo un KPI.
        WidgetDisponibleDto kpi = dashboard.getWidgets().get(0);
        assertThat(kpi.getTipoVisualizacion()).isEqualTo("KPI");
        assertThat(kpi.getAncho()).isEqualTo(3);

        // A y B: la línea sigue siendo línea, temporal y mensual.
        WidgetDisponibleDto linea = dashboard.getWidgets().get(1);
        assertThat(linea.getTipoVisualizacion()).isEqualTo("LINEAS");
        assertThat(linea.getTipoResultadoWidget()).isEqualTo("SERIE_TEMPORAL");
        assertThat(linea.getConfiguracionWidget().getGranularidad()).isEqualTo(Granularidad.MES);

        // C: la tabla temporal conserva TABLA + SERIE_TEMPORAL.
        WidgetDisponibleDto tabla = dashboard.getWidgets().get(2);
        assertThat(tabla.getTipoVisualizacion()).isEqualTo("TABLA");
        assertThat(tabla.getTipoResultadoWidget()).isEqualTo("SERIE_TEMPORAL");
    }

    /**
     * El catálogo distingue el título QUE SE LEE del título propio del widget.
     *
     * <p>Si el bloque copiara siempre el texto visible, congelaría el nombre de
     * la métrica: renombrar el indicador dejaría el informe mostrando el nombre
     * antiguo para siempre. Solo se copia cuando el usuario puso un título suyo.
     */
    @Test
    void elCatalogo_separaElTituloPropioDelNombreDeLaMetrica() {
        Long datasetId = crearDataset("ILQ 2026");
        Long panelId = crearPanel(datasetId, "Dashboard ILQ");

        Long sinTitulo = anadirWidget(panelId, crearMetricaTasa(datasetId),
                TipoVisualizacion.KPI, null, null, 3);
        Long conTitulo = anadirWidget(panelId, crearMetricaTasa(datasetId),
                TipoVisualizacion.KPI, null, null, 3);
        PanelMetrica pm = panelMetricaRepository.findById(conTitulo).orElseThrow();
        pm.setTituloPersonalizado("ILQ acumulada 2026");
        panelMetricaRepository.save(pm);

        List<WidgetDisponibleDto> widgets = catalogoService.obtener().getDashboards().stream()
                .filter(d -> d.getPanelId().equals(panelId)).findFirst().orElseThrow()
                .getWidgets();

        WidgetDisponibleDto heredado = widgets.stream()
                .filter(w -> w.getPanelMetricaId().equals(sinTitulo)).findFirst().orElseThrow();
        assertThat(heredado.getTitulo()).as("se muestra el nombre de la metrica").isNotBlank();
        assertThat(heredado.getTituloPersonalizado())
                .as("pero no hay titulo propio que copiar al bloque")
                .isNull();

        WidgetDisponibleDto propio = widgets.stream()
                .filter(w -> w.getPanelMetricaId().equals(conTitulo)).findFirst().orElseThrow();
        assertThat(propio.getTitulo()).isEqualTo("ILQ acumulada 2026");
        assertThat(propio.getTituloPersonalizado()).isEqualTo("ILQ acumulada 2026");
    }

    /**
     * A–C (continuación): lo que se persiste en el bloque es lo mismo que traía
     * el catálogo, sin degradarse por el camino.
     */
    @Test
    void insertarUnWidgetDelCatalogo_persisteSuConfiguracionSinDegradarla() {
        Long datasetId = crearDataset("ILQ 2026");
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");
        registro(datasetId, LocalDate.of(2026, 2, 6), "NO");
        Long metricaId = crearMetricaTasa(datasetId);
        Long panelId = crearPanel(datasetId, "Dashboard ILQ");
        anadirWidget(panelId, metricaId, TipoVisualizacion.LINEAS,
                TipoResultadoWidget.SERIE_TEMPORAL, Granularidad.MES, 12);

        WidgetDisponibleDto widget = catalogoService.obtener().getDashboards().stream()
                .filter(d -> d.getPanelId().equals(panelId)).findFirst().orElseThrow()
                .getWidgets().get(0);

        InformeClinicoResponseDto informe = crearInforme("Informe");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.GRAFICA);
        peticion.setMetricaId(widget.getMetricaId());
        peticion.setTipoVisualizacion(TipoVisualizacion.valueOf(widget.getTipoVisualizacion()));
        peticion.setTipoResultadoWidget(TipoResultadoWidget.valueOf(widget.getTipoResultadoWidget()));
        peticion.setConfiguracionWidget(widget.getConfiguracionWidget());
        peticion.setAncho(widget.getAncho());
        peticion.setTituloPersonalizado(widget.getTituloPersonalizado());

        BloqueInformeResponseDto creado =
                informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        assertThat(creado.getTipoVisualizacion()).as("no se convierte en BARRAS").isEqualTo("LINEAS");
        assertThat(creado.getTipoResultadoWidget()).isEqualTo("SERIE_TEMPORAL");
        assertThat(creado.getConfiguracionWidget().getGranularidad()).isEqualTo(Granularidad.MES);
        assertThat(creado.getAncho()).isEqualTo(12);
    }

    /**
     * E y F: insertar en el informe no toca el dashboard, y ambos dan los
     * mismos números porque los calcula el mismo motor.
     */
    @Test
    void insertarEnElInforme_noAlteraElDashboardYAmbosCoinciden() {
        Long datasetId = crearDataset("ILQ 2026");
        registro(datasetId, LocalDate.of(2026, 1, 5), "SI");
        registro(datasetId, LocalDate.of(2026, 1, 6), "NO");
        Long metricaId = crearMetricaTasa(datasetId);
        Long panelId = crearPanel(datasetId, "Dashboard ILQ");
        Long panelMetricaId = anadirWidget(
                panelId, metricaId, TipoVisualizacion.KPI, null, null, 3);

        InformeClinicoResponseDto informe = crearInforme("Informe");
        BloqueInformeRequestDto peticion = bloque(TipoBloqueInforme.KPI);
        peticion.setMetricaId(metricaId);
        informeService.anadirBloque(informe.getId(), primeraPagina(informe), peticion);

        // E: el widget original sigue exactamente igual.
        PanelMetrica original = panelMetricaRepository.findById(panelMetricaId).orElseThrow();
        assertThat(original.getTipoVisualizacion()).isEqualTo(TipoVisualizacion.KPI);
        assertThat(original.getAncho()).isEqualTo(3);
        assertThat(original.getActiva()).isTrue();

        // F: mismo dato en el dashboard y en el informe.
        Double enDashboard = dashboardPanelService
                .obtenerDashboard(panelId, new DashboardPanelRequestDto())
                .getWidgets().get(0).getResultadoActual().getValor();
        Double enInforme = informeService.obtenerConResultados(informe.getId())
                .getPaginas().get(0).getBloques().get(0)
                .getWidget().getResultadoActual().getValor();

        assertThat(enInforme).isEqualTo(enDashboard).isEqualTo(50.0);
    }

    /** §6: una métrica que no está en ningún dashboard se ofrece aparte. */
    @Test
    void metricaSinDashboard_seOfreceEnLaSeccionSecundaria() {
        Long datasetId = crearDataset("ILQ 2026");
        Long enPanel = crearMetricaTasa(datasetId);
        Long suelta = crearMetricaTasa(datasetId);
        Long panelId = crearPanel(datasetId, "Dashboard ILQ");
        anadirWidget(panelId, enPanel, TipoVisualizacion.KPI, null, null, 3);

        CatalogoInformeResponseDto catalogo = catalogoService.obtener();

        assertThat(catalogo.getIndicadoresSinDashboard())
                .extracting(IndicadorDisponibleDto::getMetricaId)
                .contains(suelta)
                .doesNotContain(enPanel);
        assertThat(catalogo.getIndicadoresSinDashboard())
                .filteredOn(i -> i.getMetricaId().equals(suelta))
                .singleElement()
                .satisfies(i -> assertThat(i.getDatasetNombre()).isEqualTo("ILQ 2026"));
    }

    /**
     * G: el catálogo es estructural y no ejecuta los cálculos clínicos.
     *
     * <p>Se cuenta cuántos registros clínicos carga Hibernate, no cuánto tarda.
     * La versión por tiempo de esta prueba era inestable —dependía de la caché
     * del motor y del volcado de inserts pendientes— y una prueba que falla sin
     * que nadie haya roto nada acaba ignorándose. Aquí el criterio es exacto:
     * describir qué widgets existen debe leer CERO filas de datos de pacientes,
     * y el dashboard del mismo panel demuestra que sí las habría si se calculara.
     */
    @Test
    void elCatalogo_noEjecutaLosCalculosClinicos() {
        Long datasetId = crearDataset("ILQ con volumen");
        for (int i = 0; i < 300; i++) {
            registro(datasetId, LocalDate.of(2026, 1 + (i % 12), 1 + (i % 27)), i % 20 == 0 ? "SI" : "NO");
        }
        Long panelId = crearPanel(datasetId, "Dashboard ILQ");
        for (int i = 0; i < 5; i++) {
            anadirWidget(panelId, crearMetricaTasa(datasetId), TipoVisualizacion.LINEAS,
                    TipoResultadoWidget.SERIE_TEMPORAL, Granularidad.MES, 12);
        }
        // Se vuelcan los inserts pendientes y se vacía el contexto: si los 300
        // registros siguieran en la sesión, el dashboard los reutilizaría sin
        // ir a la base de datos y no habría nada que contar.
        entityManager.flush();
        entityManager.clear();

        Statistics estadisticas = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        String registros = RegistroClinicoGenerico.class.getName();

        estadisticas.clear();
        CatalogoInformeResponseDto catalogo = catalogoService.obtener();
        long filasCatalogo = estadisticas.getEntityStatistics(registros).getLoadCount();

        estadisticas.clear();
        dashboardPanelService.obtenerDashboard(panelId, new DashboardPanelRequestDto());
        long filasDashboard = estadisticas.getEntityStatistics(registros).getLoadCount();

        assertThat(catalogo.getDashboards().stream()
                .filter(d -> d.getPanelId().equals(panelId)).findFirst().orElseThrow()
                .getWidgets()).hasSize(5);
        assertThat(filasCatalogo)
                .as("el catalogo no debe leer ninguna fila clinica")
                .isZero();
        assertThat(filasDashboard)
                .as("el dashboard si las lee: la comparacion tiene sentido")
                .isPositive();
    }

    /** Listar y eliminar el informe. */
    @Test
    void listarYEliminarInformes() {
        InformeClinicoResponseDto informe = crearInforme("Informe a eliminar");

        assertThat(informeService.listar()).extracting(InformeClinicoResponseDto::getId)
                .contains(informe.getId());

        informeService.eliminar(informe.getId());

        assertThat(informeService.listar()).extracting(InformeClinicoResponseDto::getId)
                .doesNotContain(informe.getId());
        assertThatThrownBy(() -> informeService.obtenerEstructura(informe.getId()))
                .hasMessageContaining("eliminado");
    }
}
