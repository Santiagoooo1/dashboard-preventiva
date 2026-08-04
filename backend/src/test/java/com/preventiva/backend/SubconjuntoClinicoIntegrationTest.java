package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.SubconjuntoPaginaDto;
import com.preventiva.backend.dto.SubconjuntoPerfilDto;
import com.preventiva.backend.dto.SubconjuntoRequestDto;
import com.preventiva.backend.dto.SubconjuntoResumenDto;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.PanelClinicoService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import com.preventiva.backend.service.interfaces.SubconjuntoClinicoService;
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
 * Fase 6.9H.3 — detalle del subconjunto: pacientes únicos frente a registros,
 * paginación, orden, búsqueda y perfil agregado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubconjuntoClinicoIntegrationTest {

    private static final String PREFIJO = "TEST_69H3_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private PanelClinicoService panelClinicoService;
    @Autowired private SubconjuntoClinicoService subconjuntoClinicoService;

    private String codigoUnico(String sufijo) {
        return PREFIJO + sufijo + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    private Long crearDataset(String sufijo) {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(codigoUnico(sufijo));
        r.setNombre("Test 69H3 " + sufijo);
        return datasetClinicoService.crear(r).getId();
    }

    private void crearCampo(Long datasetId, String codigo, TipoDatoExcel tipo) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(codigo);
        r.setTipoDato(tipo);
        r.setEsComun(true);
        r.setObligatorio(false);
        campoClinicoService.crear(datasetId, r);
    }

    private void crearRegistro(Long datasetId, String paciente, String sexo, String proc, Integer edad, LocalDate fecha) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setSexo(sexo);
        r.setProcedimiento(proc);
        r.setEdad(edad);
        r.setFechaEvento(fecha);
        registroClinicoGenericoService.crear(r);
    }

    private Long crearPanel(Long datasetId) {
        PanelClinicoRequestDto r = new PanelClinicoRequestDto();
        r.setCodigo(codigoUnico("PANEL"));
        r.setNombre("Panel 69H3");
        return panelClinicoService.crear(datasetId, r).getId();
    }

    private SubconjuntoRequestDto peticion(List<FiltroMetricaDto> filtros) {
        SubconjuntoRequestDto r = new SubconjuntoRequestDto();
        r.setFiltros(filtros);
        r.setCampoIndividuo("pacienteCodigo");
        return r;
    }

    private FiltroMetricaDto filtro(String campo, OperadorFiltro op, Object valor) {
        FiltroMetricaDto f = new FiltroMetricaDto();
        f.setCampo(campo);
        f.setOperador(op);
        f.setValor(valor);
        return f;
    }

    /** Dataset base: 4 registros, 3 pacientes (P1 tiene DOS registros). */
    private Long datasetConCamposYRegistros(String sufijo) {
        Long datasetId = crearDataset(sufijo);
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "sexo", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "procedimiento", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "edad", TipoDatoExcel.ENTERO);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA);

        crearRegistro(datasetId, "P1", "MUJER", "COLECISTECTOMIA", 40, LocalDate.of(2026, 1, 10));
        crearRegistro(datasetId, "P1", "MUJER", "COLECISTECTOMIA", 41, LocalDate.of(2026, 1, 20));
        crearRegistro(datasetId, "P2", "HOMBRE", "COLECISTECTOMIA", 60, LocalDate.of(2026, 1, 15));
        crearRegistro(datasetId, "P3", "HOMBRE", "APENDICECTOMIA", 30, LocalDate.of(2026, 2, 5));
        return datasetId;
    }

    // ---- Resumen: pacientes únicos != registros ----

    @Test
    void resumen_distinguePacientesUnicosDeRegistros() {
        Long datasetId = datasetConCamposYRegistros("RESUMEN");
        Long panelId = crearPanel(datasetId);

        SubconjuntoResumenDto todo = subconjuntoClinicoService.resumen(panelId, peticion(List.of()));
        assertThat(todo.getTotalRegistros()).isEqualTo(4);
        assertThat(todo.getTotalPacientesUnicos()).isEqualTo(3);
        assertThat(todo.isTienePacientes()).isTrue();
        assertThat(todo.getPeriodoDesde()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(todo.getPeriodoHasta()).isEqualTo(LocalDate.of(2026, 2, 5));

        // Selección categórica: 3 registros pero solo 2 pacientes (P1 repite).
        SubconjuntoResumenDto colecis = subconjuntoClinicoService.resumen(
                panelId, peticion(List.of(filtro("procedimiento", OperadorFiltro.EQ, "COLECISTECTOMIA"))));
        assertThat(colecis.getTotalRegistros()).isEqualTo(3);
        assertThat(colecis.getTotalPacientesUnicos()).isEqualTo(2);
    }

    @Test
    void resumen_conSeleccionTemporalYFiltroGlobalCombinadosEnAnd() {
        Long datasetId = datasetConCamposYRegistros("TEMPORAL");
        Long panelId = crearPanel(datasetId);

        // Rango de enero, como lo enviaría una selección temporal (GTE/LTE).
        SubconjuntoResumenDto enero = subconjuntoClinicoService.resumen(panelId, peticion(List.of(
                filtro("fechaEvento", OperadorFiltro.GTE, "2026-01-01"),
                filtro("fechaEvento", OperadorFiltro.LTE, "2026-01-31"))));
        assertThat(enero.getTotalRegistros()).isEqualTo(3);
        assertThat(enero.getTotalPacientesUnicos()).isEqualTo(2);

        // Global (sexo=MUJER) AND temporal (enero): solo los dos de P1.
        SubconjuntoResumenDto mujeresEnero = subconjuntoClinicoService.resumen(panelId, peticion(List.of(
                filtro("sexo", OperadorFiltro.EQ, "MUJER"),
                filtro("fechaEvento", OperadorFiltro.GTE, "2026-01-01"),
                filtro("fechaEvento", OperadorFiltro.LTE, "2026-01-31"))));
        assertThat(mujeresEnero.getTotalRegistros()).isEqualTo(2);
        assertThat(mujeresEnero.getTotalPacientesUnicos()).isEqualTo(1);
    }

    @Test
    void resumen_sinPistaDelCliente_elBackendResuelveElCampoIndividuo() {
        // El backend es la autoridad: aunque el cliente no indique nada, si el
        // dataset tiene un identificador canónico (pacienteCodigo) la vista de
        // pacientes debe estar disponible.
        Long datasetId = datasetConCamposYRegistros("SINPISTA");
        Long panelId = crearPanel(datasetId);

        SubconjuntoRequestDto sinPista = new SubconjuntoRequestDto();
        sinPista.setFiltros(List.of());

        SubconjuntoResumenDto resumen = subconjuntoClinicoService.resumen(panelId, sinPista);
        assertThat(resumen.getTotalRegistros()).isEqualTo(4);
        assertThat(resumen.isTienePacientes()).isTrue();
        assertThat(resumen.getCampoIndividuo()).isEqualTo("pacienteCodigo");
        assertThat(resumen.getTotalPacientesUnicos()).isEqualTo(3);
    }

    // ---- Pacientes: una fila por individuo ----

    @Test
    void pacientes_agrupaRegistrosDelMismoIndividuoEnUnaFila() {
        Long datasetId = datasetConCamposYRegistros("PACIENTES");
        Long panelId = crearPanel(datasetId);

        SubconjuntoPaginaDto pagina = subconjuntoClinicoService.pacientes(panelId, peticion(List.of()));

        assertThat(pagina.getTotalElementos()).isEqualTo(3);
        assertThat(pagina.getContenido()).hasSize(3);

        FilaClave p1 = FilaClave.de(pagina, "P1");
        assertThat(p1.numeroRegistros()).isEqualTo(2);
        // Edad del registro más reciente (41 del 20-ene), nunca el promedio.
        assertThat(p1.valor("edad")).isEqualTo(41);
        // Valor consistente en sus dos registros: se muestra tal cual.
        assertThat(p1.valor("sexo")).isEqualTo("MUJER");
        // Última fecha del subconjunto para ese paciente.
        assertThat(p1.valor("fechaEvento")).isEqualTo(LocalDate.of(2026, 1, 20));

        assertThat(FilaClave.de(pagina, "P2").numeroRegistros()).isEqualTo(1);
    }

    @Test
    void pacientes_conValoresDistintosEnElMismoPaciente_muestraVarios() {
        Long datasetId = crearDataset("VARIOS");
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "procedimiento", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA);
        crearRegistro(datasetId, "P1", null, "COLECISTECTOMIA", null, LocalDate.of(2026, 1, 10));
        crearRegistro(datasetId, "P1", null, "APENDICECTOMIA", null, LocalDate.of(2026, 1, 20));
        Long panelId = crearPanel(datasetId);

        SubconjuntoPaginaDto pagina = subconjuntoClinicoService.pacientes(panelId, peticion(List.of()));

        // No se elige uno arbitrariamente ni se oculta la discrepancia.
        assertThat(FilaClave.de(pagina, "P1").valor("procedimiento")).isEqualTo("Varios");
    }

    // ---- Registros: paginación, orden y búsqueda ----

    @Test
    void registros_paginaConTotalRealYOrdenPorFechaDescendente() {
        Long datasetId = datasetConCamposYRegistros("REGISTROS");
        Long panelId = crearPanel(datasetId);

        SubconjuntoRequestDto req = peticion(List.of());
        req.setTamano(2);
        req.setPagina(0);

        SubconjuntoPaginaDto primera = subconjuntoClinicoService.registros(panelId, req);
        assertThat(primera.getTotalElementos()).isEqualTo(4);
        assertThat(primera.getTotalPaginas()).isEqualTo(2);
        assertThat(primera.getContenido()).hasSize(2);
        // Más reciente primero: 05-feb.
        assertThat(primera.getContenido().get(0).getValores().get("fechaEvento"))
                .isEqualTo(LocalDate.of(2026, 2, 5));

        req.setPagina(1);
        SubconjuntoPaginaDto segunda = subconjuntoClinicoService.registros(panelId, req);
        assertThat(segunda.getContenido()).hasSize(2);
        assertThat(segunda.getTotalElementos()).isEqualTo(4);
    }

    @Test
    void registros_ordenExplicitoAscendentePorEdad() {
        Long datasetId = datasetConCamposYRegistros("ORDEN");
        Long panelId = crearPanel(datasetId);

        SubconjuntoRequestDto req = peticion(List.of());
        req.setOrdenCampo("edad");
        req.setOrdenDireccion("ASC");

        SubconjuntoPaginaDto pagina = subconjuntoClinicoService.registros(panelId, req);
        assertThat(pagina.getContenido().get(0).getValores().get("edad")).isEqualTo(30);
        assertThat(pagina.getContenido().get(3).getValores().get("edad")).isEqualTo(60);
    }

    @Test
    void registros_busquedaPorIdentificadorActuaDentroDelSubconjunto() {
        Long datasetId = datasetConCamposYRegistros("BUSQUEDA");
        Long panelId = crearPanel(datasetId);

        SubconjuntoRequestDto req = peticion(List.of());
        req.setBusquedaIndividuo("p1");  // sin distinguir mayúsculas

        SubconjuntoPaginaDto pagina = subconjuntoClinicoService.registros(panelId, req);
        assertThat(pagina.getTotalElementos()).isEqualTo(2);

        req.setBusquedaIndividuo("NO_EXISTE");
        assertThat(subconjuntoClinicoService.registros(panelId, req).getTotalElementos()).isZero();
    }

    @Test
    void registros_tamanoDePaginaSeLimitaAlMaximo() {
        Long datasetId = datasetConCamposYRegistros("TOPE");
        Long panelId = crearPanel(datasetId);

        SubconjuntoRequestDto req = peticion(List.of());
        req.setTamano(100000);

        assertThat(subconjuntoClinicoService.registros(panelId, req).getTamano()).isEqualTo(200);
    }

    @Test
    void subconjuntoVacio_devuelvePaginaSinContenidoPeroConColumnas() {
        Long datasetId = datasetConCamposYRegistros("VACIO");
        Long panelId = crearPanel(datasetId);

        SubconjuntoPaginaDto pagina = subconjuntoClinicoService.registros(
                panelId, peticion(List.of(filtro("procedimiento", OperadorFiltro.EQ, "NO_EXISTE"))));

        assertThat(pagina.getTotalElementos()).isZero();
        assertThat(pagina.getContenido()).isEmpty();
        assertThat(pagina.getTotalPaginas()).isZero();
        assertThat(pagina.getColumnas()).isNotEmpty();
    }

    @Test
    void filtroConCampoInexistente_lanzaErrorFuncional() {
        Long datasetId = datasetConCamposYRegistros("CAMPOMALO");
        Long panelId = crearPanel(datasetId);

        assertThatThrownBy(() -> subconjuntoClinicoService.resumen(
                panelId, peticion(List.of(filtro("no_existe", OperadorFiltro.EQ, "X")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Perfil ----

    @Test
    void perfil_calculaEstadisticosNumericosCategoricosYFechasSobreRegistros() {
        Long datasetId = datasetConCamposYRegistros("PERFIL");
        Long panelId = crearPanel(datasetId);

        SubconjuntoPerfilDto perfil = subconjuntoClinicoService.perfil(panelId, peticion(List.of()));

        assertThat(perfil.getBaseCalculo()).isEqualTo("REGISTROS");
        assertThat(perfil.getTotalRegistros()).isEqualTo(4);
        assertThat(perfil.getTotalPacientesUnicos()).isEqualTo(3);
        assertThat(perfil.getRegistrosPorPaciente()).isEqualTo(1.33);

        // edad: 30, 40, 41, 60 -> media 42.75, mediana (40+41)/2 = 40.5
        var edad = perfil.getNumericos().stream().filter(n -> n.getCampo().equals("edad")).findFirst().orElseThrow();
        assertThat(edad.getValoresValidos()).isEqualTo(4);
        assertThat(edad.getValoresAusentes()).isZero();
        assertThat(edad.getMedia()).isEqualTo(42.75);
        assertThat(edad.getMediana()).isEqualTo(40.5);
        assertThat(edad.getMinimo()).isEqualTo(30.0);
        assertThat(edad.getMaximo()).isEqualTo(60.0);

        var sexo = perfil.getCategoricos().stream().filter(c -> c.getCampo().equals("sexo")).findFirst().orElseThrow();
        assertThat(sexo.getValoresValidos()).isEqualTo(4);
        assertThat(sexo.getCategorias()).hasSize(2);
        // Porcentajes sobre valores válidos: 2 de 4 = 50 %.
        assertThat(sexo.getCategorias().get(0).getPorcentaje()).isEqualTo(50.0);

        var fecha = perfil.getFechas().stream().filter(f -> f.getCampo().equals("fechaEvento")).findFirst().orElseThrow();
        assertThat(fecha.getPrimera()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(fecha.getUltima()).isEqualTo(LocalDate.of(2026, 2, 5));

        // El identificador describe al individuo, no al grupo: fuera del perfil.
        assertThat(perfil.getCategoricos()).noneMatch(c -> c.getCampo().equals("pacienteCodigo"));
    }

    @Test
    void perfil_contabilizaValoresAusentes() {
        Long datasetId = crearDataset("AUSENTES");
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "sexo", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "edad", TipoDatoExcel.ENTERO);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA);
        crearRegistro(datasetId, "P1", "MUJER", 40, LocalDate.of(2026, 1, 10));
        crearRegistro(datasetId, "P2", null, null, LocalDate.of(2026, 1, 11));
        Long panelId = crearPanel(datasetId);

        SubconjuntoPerfilDto perfil = subconjuntoClinicoService.perfil(panelId, peticion(List.of()));

        var edad = perfil.getNumericos().stream().filter(n -> n.getCampo().equals("edad")).findFirst().orElseThrow();
        assertThat(edad.getValoresValidos()).isEqualTo(1);
        assertThat(edad.getValoresAusentes()).isEqualTo(1);

        var sexo = perfil.getCategoricos().stream().filter(c -> c.getCampo().equals("sexo")).findFirst().orElseThrow();
        assertThat(sexo.getValoresValidos()).isEqualTo(1);
        assertThat(sexo.getValoresAusentes()).isEqualTo(1);
    }

    private void crearRegistro(Long datasetId, String paciente, String sexo, Integer edad, LocalDate fecha) {
        crearRegistro(datasetId, paciente, sexo, null, edad, fecha);
    }

    /** Acceso legible a una fila concreta de la página. */
    private record FilaClave(Integer numeroRegistros, java.util.Map<String, Object> valores) {
        static FilaClave de(SubconjuntoPaginaDto pagina, String clave) {
            var fila = pagina.getContenido().stream()
                    .filter(f -> clave.equals(f.getClave()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No hay fila con clave " + clave));
            return new FilaClave(fila.getNumeroRegistros(), fila.getValores());
        }

        Object valor(String campo) {
            return valores.get(campo);
        }
    }
}
