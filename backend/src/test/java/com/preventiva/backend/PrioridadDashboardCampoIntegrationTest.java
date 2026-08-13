package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoMetadataDto;
import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.PerfilCampoDto;
import com.preventiva.backend.dto.PropuestaDashboardResponseDto;
import com.preventiva.backend.dto.PropuestaWidgetDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.RolAnaliticoCampo;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.DatasetFrontendMetadataService;
import com.preventiva.backend.service.interfaces.PerfilCampoService;
import com.preventiva.backend.service.interfaces.PropuestaDashboardService;
import com.preventiva.backend.service.interfaces.RegistroClinicoGenericoService;
import com.preventiva.backend.util.PrioridadCampoUtil;

import jakarta.persistence.EntityManager;
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

/**
 * Fase 6.9J.1 — prioridad explícita del campo para dashboards.
 *
 * <p>Lo que se comprueba, sobre todo, es que los tres atributos del campo son
 * INDEPENDIENTES: {@code obligatorio} (calidad del dato), {@code esComun}
 * (estructura del modelo) y {@code prioridadDashboard} (relevancia analítica).
 * Antes los dos primeros se usaban como sustitutos del tercero.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrioridadDashboardCampoIntegrationTest {

    private static final String PREFIJO = "TEST_69J1_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private DatasetFrontendMetadataService datasetFrontendMetadataService;
    @Autowired private PerfilCampoService perfilCampoService;
    @Autowired private PropuestaDashboardService propuestaDashboardService;
    @Autowired private CampoClinicoRepository campoClinicoRepository;
    @Autowired private EntityManager entityManager;

    // ------------------------------------------------------------------

    private String codigoUnico(String s) {
        return PREFIJO + s + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(codigoUnico("DS"));
        r.setNombre("Test 6.9J.1");
        return datasetClinicoService.crear(r).getId();
    }

    private CampoClinicoRequestDto peticion(
            String codigo, TipoDatoExcel tipo, boolean comun, boolean obligatorio,
            PrioridadDashboardCampo prioridad) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(codigo);
        r.setTipoDato(tipo);
        r.setEsComun(comun);
        r.setObligatorio(obligatorio);
        r.setPrioridadDashboard(prioridad);
        return r;
    }

    private CampoClinicoResponseDto crearCampo(
            Long datasetId, String codigo, TipoDatoExcel tipo, boolean comun, boolean obligatorio,
            PrioridadDashboardCampo prioridad) {
        return campoClinicoService.crear(datasetId, peticion(codigo, tipo, comun, obligatorio, prioridad));
    }

    private CampoClinico entidad(Long campoId) {
        // Se vacía el contexto de persistencia para leer de verdad de la base y
        // no del caché de primer nivel: si algo no se hubiera persistido, el
        // caché lo taparía.
        entityManager.flush();
        entityManager.clear();
        return campoClinicoRepository.findById(campoId).orElseThrow();
    }

    /** Simula una fila anterior a esta fase: columna sin prioridad todavía. */
    private void dejarPrioridadANull(Long campoId) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "UPDATE campos_clinicos SET prioridad_dashboard = NULL WHERE id = :id")
                .setParameter("id", campoId)
                .executeUpdate();
        entityManager.clear();
    }

    /** El mismo relleno que aplica BackfillPrioridadDashboard al arrancar. */
    private void ejecutarBackfill() {
        entityManager.createNativeQuery("""
                UPDATE campos_clinicos
                SET prioridad_dashboard = CASE
                    WHEN es_comun THEN 'FUNDAMENTAL'
                    WHEN obligatorio THEN 'IMPORTANTE'
                    ELSE 'NORMAL'
                END
                WHERE prioridad_dashboard IS NULL
                """).executeUpdate();
        entityManager.clear();
    }

    // ==================================================================
    // A, B, C — reglas de migración sobre datos ya existentes
    // ==================================================================

    @Test
    void backfillA_unCampoComunPasaAFundamental() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true, false, null).getId();
        dejarPrioridadANull(campoId);

        ejecutarBackfill();

        assertThat(entidad(campoId).getPrioridadDashboard())
                .isEqualTo(PrioridadDashboardCampo.FUNDAMENTAL);
    }

    @Test
    void backfillB_unCampoObligatorioNoComunPasaAImportante() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(datasetId, "ilq", TipoDatoExcel.BOOLEANO, false, true, null).getId();
        dejarPrioridadANull(campoId);

        ejecutarBackfill();

        assertThat(entidad(campoId).getPrioridadDashboard())
                .isEqualTo(PrioridadDashboardCampo.IMPORTANTE);
    }

    @Test
    void backfillC_unCampoCorrientePasaANormal() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(datasetId, "notas", TipoDatoExcel.TEXTO, false, false, null).getId();
        dejarPrioridadANull(campoId);

        ejecutarBackfill();

        assertThat(entidad(campoId).getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.NORMAL);
    }

    @Test
    void backfillNuncaDeduceExcluir() {
        Long datasetId = crearDataset();
        List<Long> ids = List.of(
                crearCampo(datasetId, "a", TipoDatoExcel.TEXTO, true, true, null).getId(),
                crearCampo(datasetId, "b", TipoDatoExcel.TEXTO, false, true, null).getId(),
                crearCampo(datasetId, "c", TipoDatoExcel.TEXTO, false, false, null).getId());
        ids.forEach(this::dejarPrioridadANull);

        ejecutarBackfill();

        // Apartar una columna del análisis es una decisión clínica: no se
        // adivina nunca, o se estarían escondiendo datos sin pedirlo.
        for (Long id : ids) {
            assertThat(entidad(id).getPrioridadDashboard())
                    .as("campo %s", id)
                    .isNotEqualTo(PrioridadDashboardCampo.EXCLUIR);
        }
    }

    @Test
    void elBackfillNoPisaUnaPrioridadYaDecidida() {
        Long datasetId = crearDataset();
        // Común pero marcado a mano como EXCLUIR: el relleno diría FUNDAMENTAL.
        Long campoId = crearCampo(
                datasetId, "sexo", TipoDatoExcel.TEXTO, true, true, PrioridadDashboardCampo.EXCLUIR).getId();

        ejecutarBackfill();

        assertThat(entidad(campoId).getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.EXCLUIR);
    }

    // ==================================================================
    // D — EXCLUIR se guarda y se recupera
    // ==================================================================

    @Test
    void excluirSeGuardaYSeRecupera() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(
                datasetId, "observaciones", TipoDatoExcel.TEXTO, false, false,
                PrioridadDashboardCampo.EXCLUIR).getId();

        assertThat(entidad(campoId).getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.EXCLUIR);
        assertThat(campoClinicoService.listarPorDataset(datasetId))
                .filteredOn(c -> c.getId().equals(campoId))
                .allSatisfy(c -> assertThat(c.getPrioridadDashboard()).isEqualTo("EXCLUIR"));
    }

    @Test
    void seGuardaYRecuperaCadaUnoDeLosCuatroValores() {
        Long datasetId = crearDataset();

        for (PrioridadDashboardCampo p : PrioridadDashboardCampo.values()) {
            Long id = crearCampo(datasetId, "campo_" + p.name().toLowerCase(),
                    TipoDatoExcel.TEXTO, false, false, p).getId();
            assertThat(entidad(id).getPrioridadDashboard()).as("valor %s", p).isEqualTo(p);
        }
    }

    @Test
    void unCampoNuevoSinIndicacionNaceNormal() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(datasetId, "otro", TipoDatoExcel.TEXTO, true, true, null).getId();

        // Aunque sea común Y obligatorio: la prioridad ya NO se deduce de eso.
        assertThat(entidad(campoId).getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.NORMAL);
    }

    // ==================================================================
    // E y F — independencia entre los tres atributos
    // ==================================================================

    @Test
    void cambiarLaPrioridadNoCambiaObligatorioNiEsComun() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(
                datasetId, "edad", TipoDatoExcel.ENTERO, true, true, PrioridadDashboardCampo.NORMAL).getId();

        CampoClinicoRequestDto edicion =
                peticion("edad", TipoDatoExcel.ENTERO, true, true, PrioridadDashboardCampo.EXCLUIR);
        campoClinicoService.actualizar(datasetId, campoId, edicion);

        CampoClinico tras = entidad(campoId);
        assertThat(tras.getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.EXCLUIR);
        assertThat(tras.getObligatorio()).isTrue();
        assertThat(tras.getEsComun()).isTrue();
    }

    @Test
    void cambiarObligatorioNoCambiaLaPrioridad() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(
                datasetId, "cultivo", TipoDatoExcel.BOOLEANO, false, false,
                PrioridadDashboardCampo.FUNDAMENTAL).getId();

        // Se pasa a obligatorio sin tocar la prioridad.
        campoClinicoService.actualizar(datasetId, campoId,
                peticion("cultivo", TipoDatoExcel.BOOLEANO, false, true, null));

        CampoClinico tras = entidad(campoId);
        assertThat(tras.getObligatorio()).isTrue();
        assertThat(tras.getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.FUNDAMENTAL);
    }

    @Test
    void editarSinEnviarPrioridadLaConserva() {
        Long datasetId = crearDataset();
        Long campoId = crearCampo(
                datasetId, "asa", TipoDatoExcel.TEXTO, false, false,
                PrioridadDashboardCampo.IMPORTANTE).getId();

        // Un cliente que solo cambia la etiqueta no debe borrar una decisión
        // que alguien ya había tomado.
        campoClinicoService.actualizar(datasetId, campoId,
                peticion("asa", TipoDatoExcel.TEXTO, false, false, null));

        assertThat(entidad(campoId).getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.IMPORTANTE);
    }

    // ==================================================================
    // G — la API la devuelve
    // ==================================================================

    @Test
    void laMetadataDelDatasetDevuelveLaPrioridad() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "edad", TipoDatoExcel.ENTERO, true, false, PrioridadDashboardCampo.FUNDAMENTAL);
        crearCampo(datasetId, "notas", TipoDatoExcel.TEXTO, false, false, PrioridadDashboardCampo.EXCLUIR);

        List<CampoClinicoMetadataDto> campos =
                datasetFrontendMetadataService.obtenerMetadataDataset(datasetId).getCampos();

        assertThat(campos).filteredOn(c -> c.getCodigo().equals("edad"))
                .allSatisfy(c -> assertThat(c.getPrioridadDashboard()).isEqualTo("FUNDAMENTAL"));
        assertThat(campos).filteredOn(c -> c.getCodigo().equals("notas"))
                .allSatisfy(c -> assertThat(c.getPrioridadDashboard()).isEqualTo("EXCLUIR"));
    }

    @Test
    void laMetadataDeMetricasDevuelveLaPrioridad() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "edad", TipoDatoExcel.ENTERO, true, false, PrioridadDashboardCampo.IMPORTANTE);

        assertThat(datasetFrontendMetadataService.obtenerMetadataMetricas(datasetId).getCampos())
                .filteredOn(c -> c.getCodigo().equals("edad"))
                .allSatisfy(c -> assertThat(c.getPrioridadDashboard()).isEqualTo("IMPORTANTE"));
    }

    @Test
    void elPerfilDeCamposDevuelveLaPrioridad() {
        Long datasetId = datasetConDatos(PrioridadDashboardCampo.FUNDAMENTAL);

        List<PerfilCampoDto> campos = perfilCampoService.obtenerPerfilCampos(datasetId).getCampos();

        assertThat(campos).filteredOn(c -> c.getCodigo().equals("marcado"))
                .allSatisfy(c -> assertThat(c.getPrioridadDashboard()).isEqualTo("FUNDAMENTAL"));
    }

    // ==================================================================
    // H — la propuesta usa la prioridad, no los sustitutos
    // ==================================================================

    /**
     * Dataset con dos columnas categóricas equivalentes salvo por su prioridad.
     * `marcado` lleva la que se pase; `corriente` se queda en NORMAL.
     */
    private Long datasetConDatos(PrioridadDashboardCampo prioridadDelMarcado) {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true, false, PrioridadDashboardCampo.NORMAL);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true, false, PrioridadDashboardCampo.NORMAL);
        crearCampo(datasetId, "marcado", TipoDatoExcel.TEXTO, false, false, prioridadDelMarcado);
        crearCampo(datasetId, "corriente", TipoDatoExcel.TEXTO, false, false, PrioridadDashboardCampo.NORMAL);

        for (int i = 1; i <= 10; i++) {
            RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
            r.setDatasetId(datasetId);
            r.setPacienteCodigo("P" + i);
            r.setFechaEvento(LocalDate.of(2026, 1, i));
            Map<String, Object> d = new HashMap<>();
            d.put("marcado", "VALOR_" + (i % 3));
            d.put("corriente", "OTRO_" + (i % 3));
            r.setDatosDinamicos(d);
            registroClinicoGenericoService.crear(r);
        }
        return datasetId;
    }

    private List<String> codigos(PropuestaDashboardResponseDto r) {
        return r.getPropuestas().stream().map(PropuestaWidgetDto::getCodigoMetrica).toList();
    }

    @Test
    void unCampoFundamentalSeProponeAntesQueUnoNormalEquivalente() {
        PropuestaDashboardResponseDto r =
                propuestaDashboardService.proponer(datasetConDatos(PrioridadDashboardCampo.FUNDAMENTAL));

        List<String> orden = codigos(r);
        assertThat(orden.indexOf("distribucion_marcado"))
                .isLessThan(orden.indexOf("distribucion_corriente"));
    }

    @Test
    void unCampoExcluidoNoSePropone() {
        PropuestaDashboardResponseDto r =
                propuestaDashboardService.proponer(datasetConDatos(PrioridadDashboardCampo.EXCLUIR));

        assertThat(codigos(r)).doesNotContain("distribucion_marcado");
        // El equivalente sin excluir sí sigue proponiéndose: se ha apartado esa
        // columna, no el tipo de widget.
        assertThat(codigos(r)).contains("distribucion_corriente");
    }

    @Test
    void esComunYaNoSustituyeAFundamental() {
        Long datasetId = crearDataset();
        // Común y obligatorio, pero NORMAL: antes habría puntuado 1500 de más.
        CampoClinico comun = CampoClinico.builder()
                .codigo("comun").esComun(true).obligatorio(true)
                .prioridadDashboard(PrioridadDashboardCampo.NORMAL).build();
        CampoClinico normal = CampoClinico.builder()
                .codigo("normal").esComun(false).obligatorio(false)
                .prioridadDashboard(PrioridadDashboardCampo.NORMAL).build();

        assertThat(PrioridadCampoUtil.puntuar(comun, RolAnaliticoCampo.CATEGORICO, 100, false))
                .isEqualTo(PrioridadCampoUtil.puntuar(normal, RolAnaliticoCampo.CATEGORICO, 100, false));
        assertThat(datasetId).isNotNull();
    }

    @Test
    void laPrioridadExplicitaSiCambiaLaPuntuacion() {
        CampoClinico fundamental = CampoClinico.builder()
                .codigo("a").esComun(false).obligatorio(false)
                .prioridadDashboard(PrioridadDashboardCampo.FUNDAMENTAL).build();
        CampoClinico importante = CampoClinico.builder()
                .codigo("b").esComun(false).obligatorio(false)
                .prioridadDashboard(PrioridadDashboardCampo.IMPORTANTE).build();
        CampoClinico normal = CampoClinico.builder()
                .codigo("c").esComun(false).obligatorio(false)
                .prioridadDashboard(PrioridadDashboardCampo.NORMAL).build();

        int pf = PrioridadCampoUtil.puntuar(fundamental, RolAnaliticoCampo.CATEGORICO, 100, false);
        int pi = PrioridadCampoUtil.puntuar(importante, RolAnaliticoCampo.CATEGORICO, 100, false);
        int pn = PrioridadCampoUtil.puntuar(normal, RolAnaliticoCampo.CATEGORICO, 100, false);

        assertThat(pf).isGreaterThan(pi);
        assertThat(pi).isGreaterThan(pn);
    }

    @Test
    void unCampoExcluidoNuncaMereceWidgetAunqueEsteRelleno() {
        CampoClinico excluido = CampoClinico.builder()
                .codigo("a").esComun(true).obligatorio(true)
                .prioridadDashboard(PrioridadDashboardCampo.EXCLUIR).build();

        // Ni siquiera por obligatorio: EXCLUIR manda sobre todo lo demás.
        assertThat(PrioridadCampoUtil.mereceWidget(excluido, 100.0)).isFalse();
        assertThat(PrioridadCampoUtil.mereceWidget(excluido, 0.0)).isFalse();
    }

    @Test
    void elMotivoDeLaPropuestaCitaLaPrioridadNoElEsComun() {
        CampoClinico comun = CampoClinico.builder()
                .codigo("a").esComun(true).obligatorio(true)
                .prioridadDashboard(PrioridadDashboardCampo.NORMAL).build();
        CampoClinico marcado = CampoClinico.builder()
                .codigo("b").esComun(false).obligatorio(false)
                .prioridadDashboard(PrioridadDashboardCampo.FUNDAMENTAL).build();

        assertThat(PrioridadCampoUtil.motivo(comun, RolAnaliticoCampo.CATEGORICO, 100.0))
                .doesNotContain("fundamental");
        // Desde 6.9J.2 el motivo añade también la aptitud ("...adecuada para
        // una distribución"); lo que aquí importa es que cite la prioridad.
        assertThat(PrioridadCampoUtil.motivo(marcado, RolAnaliticoCampo.CATEGORICO, 100.0))
                .startsWith("Campo marcado como fundamental");
    }
}
