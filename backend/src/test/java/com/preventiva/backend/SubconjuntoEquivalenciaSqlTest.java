package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.SubconjuntoRequestDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.PanelClinicoService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import com.preventiva.backend.service.interfaces.SubconjuntoClinicoService;
import com.preventiva.backend.util.FiltroMetricaEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El detalle del subconjunto filtra en SQL; el resto del dashboard usa
 * {@link FiltroMetricaEvaluator} en memoria. Si ambas semánticas divergieran,
 * la tabla mostraría una población distinta de la que resumen los gráficos.
 *
 * <p>Estos tests comparan AMBOS caminos sobre los casos donde es más fácil que
 * se separen: acentos, mayúsculas, espacios repetidos, vocabulario booleano
 * español, nulos, campos dinámicos JSONB y comparaciones de rango.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubconjuntoEquivalenciaSqlTest {

    private static final String PREFIJO = "TEST_69H3EQ_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private PanelClinicoService panelClinicoService;
    @Autowired private SubconjuntoClinicoService subconjuntoClinicoService;
    @Autowired private CampoClinicoRepository campoClinicoRepository;
    @Autowired private RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    private Long datasetId;
    private Long panelId;

    private String codigoUnico(String s) {
        return PREFIJO + s + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    private void crearCampo(String codigo, TipoDatoExcel tipo, boolean comun) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(codigo);
        r.setTipoDato(tipo);
        r.setEsComun(comun);
        r.setObligatorio(false);
        campoClinicoService.crear(datasetId, r);
    }

    /** Dataset con acentos, espacios raros, booleanos en vocabulario español y JSONB. */
    private void prepararDatos() {
        DatasetClinicoRequestDto d = new DatasetClinicoRequestDto();
        d.setCodigo(codigoUnico("EQ"));
        d.setNombre("Equivalencia SQL");
        datasetId = datasetClinicoService.crear(d).getId();

        crearCampo("pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo("procedimiento", TipoDatoExcel.TEXTO, true);
        crearCampo("edad", TipoDatoExcel.ENTERO, true);
        crearCampo("fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo("infeccion", TipoDatoExcel.BOOLEANO, false);
        crearCampo("asa", TipoDatoExcel.TEXTO, false);

        registrar("P1", "PRÓTESIS DE CADERA", 70, LocalDate.of(2026, 1, 5), "SI", "III");
        registrar("P2", "protesis de cadera", 65, LocalDate.of(2026, 1, 6), "NO", "II");
        registrar("P3", "PROTESIS  DE  CADERA", 55, LocalDate.of(2026, 2, 1), "X", "I");
        registrar("P4", "COLECISTECTOMÍA", 40, LocalDate.of(2026, 2, 2), "FALSO", "II");
        registrar("P5", null, null, LocalDate.of(2026, 3, 1), null, null);
        registrar("P6", "  Colecistectomia  ", 33, null, "POSITIVO", "III");

        PanelClinicoRequestDto p = new PanelClinicoRequestDto();
        p.setCodigo(codigoUnico("PANEL"));
        p.setNombre("Panel equivalencia");
        panelId = panelClinicoService.crear(datasetId, p).getId();
    }

    private void registrar(String paciente, String proc, Integer edad, LocalDate fecha, String infeccion, String asa) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setProcedimiento(proc);
        r.setEdad(edad);
        r.setFechaEvento(fecha);

        java.util.Map<String, Object> dinamicos = new java.util.HashMap<>();
        dinamicos.put("infeccion", infeccion);
        dinamicos.put("asa", asa);
        r.setDatosDinamicos(dinamicos);

        registroClinicoGenericoService.crear(r);
    }

    private FiltroMetricaDto filtro(String campo, OperadorFiltro op, Object valor) {
        FiltroMetricaDto f = new FiltroMetricaDto();
        f.setCampo(campo);
        f.setOperador(op);
        f.setValor(valor);
        return f;
    }

    /** Cuenta con el evaluador EN MEMORIA, el mismo que usan las métricas. */
    private long contarEnMemoria(List<FiltroMetricaDto> filtros) {
        Map<String, CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)
                .stream().collect(Collectors.toMap(CampoClinico::getCodigo, c -> c, (a, b) -> a));

        return registroClinicoGenericoRepository.findByDatasetId(datasetId).stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtros, campos::get))
                .count();
    }

    /** Cuenta con el camino SQL del detalle del subconjunto. */
    private long contarEnSql(List<FiltroMetricaDto> filtros) {
        SubconjuntoRequestDto req = new SubconjuntoRequestDto();
        req.setFiltros(filtros);
        req.setCampoIndividuo("pacienteCodigo");
        return subconjuntoClinicoService.resumen(panelId, req).getTotalRegistros();
    }

    private void comprobarEquivalencia(String caso, List<FiltroMetricaDto> filtros) {
        long memoria = contarEnMemoria(filtros);
        long sql = contarEnSql(filtros);
        assertThat(sql)
                .as("%s → SQL debe coincidir con el evaluador en memoria", caso)
                .isEqualTo(memoria);
    }

    @Test
    void textoConAcentosMayusculasYEspacios_coincideEnAmbosCaminos() {
        prepararDatos();

        // "PRÓTESIS DE CADERA", "protesis de cadera" y "PROTESIS  DE  CADERA"
        // son el MISMO valor tras normalizar: los tres deben contarse.
        comprobarEquivalencia("EQ con acento",
                List.of(filtro("procedimiento", OperadorFiltro.EQ, "PRÓTESIS DE CADERA")));
        comprobarEquivalencia("EQ sin acento ni mayúsculas",
                List.of(filtro("procedimiento", OperadorFiltro.EQ, "protesis de cadera")));
        comprobarEquivalencia("EQ con espacios repetidos",
                List.of(filtro("procedimiento", OperadorFiltro.EQ, "PROTESIS  DE  CADERA")));
        comprobarEquivalencia("EQ con espacios alrededor",
                List.of(filtro("procedimiento", OperadorFiltro.EQ, "  colecistectomía  ")));

        assertThat(contarEnSql(List.of(filtro("procedimiento", OperadorFiltro.EQ, "PRÓTESIS DE CADERA"))))
                .as("los tres registros equivalentes se cuentan juntos")
                .isEqualTo(3);
    }

    @Test
    void contains_yNe_incluyenNulosIgualQueElEvaluador() {
        prepararDatos();

        comprobarEquivalencia("CONTAINS acentuado",
                List.of(filtro("procedimiento", OperadorFiltro.CONTAINS, "prótesis")));
        // NE debe incluir los registros con valor nulo, como hace el evaluador.
        comprobarEquivalencia("NE con nulos",
                List.of(filtro("procedimiento", OperadorFiltro.NE, "COLECISTECTOMIA")));
        comprobarEquivalencia("IS_NULL", List.of(filtro("procedimiento", OperadorFiltro.IS_NULL, null)));
        comprobarEquivalencia("NOT_NULL", List.of(filtro("procedimiento", OperadorFiltro.NOT_NULL, null)));
    }

    @Test
    void booleanoConVocabularioEspanol_coincideEnAmbosCaminos() {
        prepararDatos();

        // SI, X y POSITIVO son verdaderos; NO y FALSO, falsos.
        comprobarEquivalencia("booleano true", List.of(filtro("infeccion", OperadorFiltro.EQ, "true")));
        comprobarEquivalencia("booleano SI", List.of(filtro("infeccion", OperadorFiltro.EQ, "SI")));
        comprobarEquivalencia("booleano false", List.of(filtro("infeccion", OperadorFiltro.EQ, "false")));
        comprobarEquivalencia("booleano NOT_NULL", List.of(filtro("infeccion", OperadorFiltro.NOT_NULL, null)));

        assertThat(contarEnSql(List.of(filtro("infeccion", OperadorFiltro.EQ, "true"))))
                .as("SI, X y POSITIVO cuentan como verdadero")
                .isEqualTo(3);
    }

    @Test
    void camposDinamicosJsonb_coincidenEnAmbosCaminos() {
        prepararDatos();

        comprobarEquivalencia("JSONB texto EQ", List.of(filtro("asa", OperadorFiltro.EQ, "III")));
        comprobarEquivalencia("JSONB texto IN", List.of(filtro("asa", OperadorFiltro.IN, List.of("I", "II"))));
        comprobarEquivalencia("JSONB NOT_IN", List.of(filtro("asa", OperadorFiltro.NOT_IN, List.of("III"))));
        comprobarEquivalencia("JSONB IS_NULL", List.of(filtro("asa", OperadorFiltro.IS_NULL, null)));
    }

    @Test
    void rangosNumericosYDeFecha_coincidenEnAmbosCaminos() {
        prepararDatos();

        comprobarEquivalencia("edad GTE", List.of(filtro("edad", OperadorFiltro.GTE, 55)));
        comprobarEquivalencia("edad LT", List.of(filtro("edad", OperadorFiltro.LT, 55)));
        comprobarEquivalencia("fecha rango enero", List.of(
                filtro("fechaEvento", OperadorFiltro.GTE, "2026-01-01"),
                filtro("fechaEvento", OperadorFiltro.LTE, "2026-01-31")));
        comprobarEquivalencia("fecha NOT_NULL", List.of(filtro("fechaEvento", OperadorFiltro.NOT_NULL, null)));
    }

    @Test
    void combinacionDeVariosFiltros_coincideEnAmbosCaminos() {
        prepararDatos();

        comprobarEquivalencia("texto AND booleano AND rango", List.of(
                filtro("procedimiento", OperadorFiltro.CONTAINS, "cadera"),
                filtro("infeccion", OperadorFiltro.EQ, "true"),
                filtro("edad", OperadorFiltro.GTE, 60)));

        comprobarEquivalencia("sin resultados", List.of(
                filtro("procedimiento", OperadorFiltro.EQ, "NO_EXISTE"),
                filtro("edad", OperadorFiltro.GT, 200)));

        comprobarEquivalencia("sin filtros", List.of());
    }

    @Test
    void countDistinctDeIndividuos_noDependeDeLaPaginacion() {
        prepararDatos();

        SubconjuntoRequestDto req = new SubconjuntoRequestDto();
        req.setFiltros(List.of());
        req.setCampoIndividuo("pacienteCodigo");
        req.setTamano(2);

        // 6 pacientes distintos aunque la página solo traiga 2 filas.
        assertThat(subconjuntoClinicoService.resumen(panelId, req).getTotalPacientesUnicos()).isEqualTo(6);
        assertThat(subconjuntoClinicoService.pacientes(panelId, req).getContenido()).hasSize(2);
        assertThat(subconjuntoClinicoService.pacientes(panelId, req).getTotalElementos()).isEqualTo(6);
    }
}
