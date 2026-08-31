package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.RegistroClinicoGenericoRepository;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
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
 * Reanudar una importación no puede duplicar campos.
 *
 * <p>El asistente creaba los campos con {@code crear()}, que rechaza códigos
 * repetidos. Al reanudar un borrador desde
 * {@code /crear-dashboard/borrador/{id}} volvía a declarar las mismas columnas
 * y la importación moría con «Ya existe un campo clínico con el código
 * pacienteCodigo en este dataset».
 *
 * <p>La protección de {@code crear()} sigue intacta —dos campos con el mismo
 * código en un dataset es un error de verdad—; lo que cambia es que el flujo de
 * importación usa {@code asegurarParaImportacion}, que reutiliza lo que ya hay.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportacionCamposExistentesIntegrationTest {

    private static final String PREFIJO = "TEST_REANUD_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private CampoClinicoRepository campoClinicoRepository;
    @Autowired private RegistroClinicoGenericoRepository registroClinicoGenericoRepository;

    // ------------------------------------------------------------------

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(PREFIJO + contador.incrementAndGet() + "_" + System.nanoTime());
        r.setNombre("Test reanudación");
        return datasetClinicoService.crear(r).getId();
    }

    private CampoClinicoRequestDto peticion(
            String codigo, TipoDatoExcel tipo, boolean comun, boolean obligatorio) {
        CampoClinicoRequestDto r = new CampoClinicoRequestDto();
        r.setCodigo(codigo);
        r.setEtiqueta(codigo);
        r.setTipoDato(tipo);
        r.setEsComun(comun);
        r.setObligatorio(obligatorio);
        return r;
    }

    /** Las columnas que el asistente declara para un Excel clínico típico. */
    private List<CampoClinicoRequestDto> columnasDelAsistente() {
        return List.of(
                peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true),
                peticion("fechaEvento", TipoDatoExcel.FECHA, true, true),
                peticion("servicio", TipoDatoExcel.TEXTO, true, false),
                peticion("procedimiento", TipoDatoExcel.TEXTO, true, false),
                peticion("edad", TipoDatoExcel.ENTERO, true, false),
                peticion("sexo", TipoDatoExcel.TEXTO, true, false));
    }

    private long cuantosCampos(Long datasetId, String codigo) {
        return campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId).stream()
                .filter(c -> c.getCodigo().equalsIgnoreCase(codigo))
                .count();
    }

    // ==================================================================
    // A, B — el caso que reventaba
    // ==================================================================

    @Test
    void A_unDatasetConPacienteCodigoPreexistenteNoFallaAlImportar() {
        Long datasetId = crearDataset();
        // Primer intento del asistente: crea los campos y luego falla más
        // adelante (validación de filas, por ejemplo).
        campoClinicoService.crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true));

        // Reanudación: el asistente vuelve a declarar las mismas columnas.
        assertThatThrownBy(() ->
                campoClinicoService.crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true)))
                .isInstanceOf(IllegalArgumentException.class);

        // Con la operación de importación, no falla.
        List<CampoClinicoResponseDto> campos =
                campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        assertThat(campos).hasSize(6);
        assertThat(campos).extracting(CampoClinicoResponseDto::getCodigo)
                .containsExactly("pacienteCodigo", "fechaEvento", "servicio", "procedimiento", "edad", "sexo");
    }

    @Test
    void B_pacienteCodigoSigueExistiendoExactamenteUnaVez() {
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true));

        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        assertThat(cuantosCampos(datasetId, "pacienteCodigo")).isEqualTo(1);
    }

    // ==================================================================
    // C — los registros sí se importan
    // ==================================================================

    @Test
    void C_losRegistrosSeImportanSobreElCampoReutilizado() {
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true));

        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        for (int i = 1; i <= 5; i++) {
            RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
            r.setDatasetId(datasetId);
            r.setPacienteCodigo("P" + i);
            r.setFechaEvento(LocalDate.of(2026, 1, i));
            registroClinicoGenericoService.crear(r);
        }

        assertThat(registroClinicoGenericoRepository.findByDatasetId(datasetId)).hasSize(5);
    }

    // ==================================================================
    // D, E — los demás campos estructurales tampoco se duplican
    // ==================================================================

    @Test
    void D_fechaEventoPreexistenteNoSeDuplica() {
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion("fechaEvento", TipoDatoExcel.FECHA, true, true));

        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        assertThat(cuantosCampos(datasetId, "fechaEvento")).isEqualTo(1);
    }

    @Test
    void E_servicioPreexistenteNoSeDuplica() {
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion("servicio", TipoDatoExcel.TEXTO, true, false));

        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        assertThat(cuantosCampos(datasetId, "servicio")).isEqualTo(1);
    }

    @Test
    void ningunCampoEstructuralSeDuplicaAlReanudar() {
        Long datasetId = crearDataset();
        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        for (String codigo : List.of(
                "pacienteCodigo", "fechaEvento", "servicio", "procedimiento", "edad", "sexo")) {
            assertThat(cuantosCampos(datasetId, codigo)).as("campo %s", codigo).isEqualTo(1);
        }
        assertThat(campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)).hasSize(6);
    }

    @Test
    void laComparacionDeCodigoIgnoraMayusculas() {
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion("PacienteCodigo", TipoDatoExcel.TEXTO, true, true));

        // Mismo código con otra caja: es el mismo campo, igual que para `crear`.
        campoClinicoService.asegurarParaImportacion(
                datasetId, List.of(peticion("pacientecodigo", TipoDatoExcel.TEXTO, true, true)));

        assertThat(campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)).hasSize(1);
    }

    // ==================================================================
    // F — una columna nueva sí se crea
    // ==================================================================

    @Test
    void F_unaColumnaNuevaSiCreaSuCampo() {
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true));

        campoClinicoService.asegurarParaImportacion(datasetId, List.of(
                peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true),
                peticion("infeccionQuirurgica", TipoDatoExcel.BOOLEANO, false, false)));

        assertThat(campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)).hasSize(2);
        assertThat(cuantosCampos(datasetId, "infeccionQuirurgica")).isEqualTo(1);
    }

    @Test
    void unCampoNuevoNaceConPrioridadNormal() {
        Long datasetId = crearDataset();

        campoClinicoService.asegurarParaImportacion(
                datasetId, List.of(peticion("nuevo", TipoDatoExcel.TEXTO, false, false)));

        CampoClinico campo = campoClinicoRepository
                .findByDatasetIdAndCodigoIgnoreCase(datasetId, "nuevo").orElseThrow();
        assertThat(campo.getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.NORMAL);
    }

    // ==================================================================
    // G, H — idempotencia y estabilidad del identificador
    // ==================================================================

    @Test
    void G_reintentarLaMismaFinalizacionNoDuplicaCampos() {
        Long datasetId = crearDataset();

        for (int intento = 1; intento <= 3; intento++) {
            campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());
        }

        assertThat(campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)).hasSize(6);
    }

    @Test
    void H_elIdentificadorDePacienteConservaElMismoCampoClinicoId() {
        Long datasetId = crearDataset();
        Long idOriginal = campoClinicoService
                .crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true))
                .getId();

        List<CampoClinicoResponseDto> primera =
                campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());
        List<CampoClinicoResponseDto> segunda =
                campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        assertThat(primera.get(0).getId()).isEqualTo(idOriginal);
        assertThat(segunda.get(0).getId()).isEqualTo(idOriginal);
    }

    @Test
    void reutilizarNoTocaEsComunObligatorioNiPrioridad() {
        Long datasetId = crearDataset();
        CampoClinicoRequestDto original = peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true);
        original.setPrioridadDashboard(PrioridadDashboardCampo.FUNDAMENTAL);
        Long campoId = campoClinicoService.crear(datasetId, original).getId();

        // La importación vuelve a declararlo con valores DISTINTOS: no debe
        // deshacer lo que el usuario configuró después de la primera vez.
        CampoClinicoRequestDto reimportado = peticion("pacienteCodigo", TipoDatoExcel.TEXTO, false, false);
        reimportado.setPrioridadDashboard(PrioridadDashboardCampo.EXCLUIR);
        campoClinicoService.asegurarParaImportacion(datasetId, List.of(reimportado));

        CampoClinico tras = campoClinicoRepository.findById(campoId).orElseThrow();
        assertThat(tras.getEsComun()).isTrue();
        assertThat(tras.getObligatorio()).isTrue();
        assertThat(tras.getPrioridadDashboard()).isEqualTo(PrioridadDashboardCampo.FUNDAMENTAL);
    }

    @Test
    void reutilizarNoCambiaElTipoDeDato() {
        Long datasetId = crearDataset();
        Long campoId = campoClinicoService
                .crear(datasetId, peticion("edad", TipoDatoExcel.ENTERO, true, false))
                .getId();

        // Si el tipo cambiara, las métricas que promedian este campo dejarían de
        // ser válidas sin que nadie lo hubiera pedido.
        campoClinicoService.asegurarParaImportacion(
                datasetId, List.of(peticion("edad", TipoDatoExcel.TEXTO, true, false)));

        assertThat(campoClinicoRepository.findById(campoId).orElseThrow().getTipoDato())
                .isEqualTo(TipoDatoExcel.ENTERO);
    }

    @Test
    void unCampoArchivadoSeReactivaAlVolverAImportarlo() {
        Long datasetId = crearDataset();
        Long campoId = campoClinicoService
                .crear(datasetId, peticion("servicio", TipoDatoExcel.TEXTO, true, false))
                .getId();
        campoClinicoService.desactivar(datasetId, campoId);

        campoClinicoService.asegurarParaImportacion(
                datasetId, List.of(peticion("servicio", TipoDatoExcel.TEXTO, true, false)));

        // Si quedara archivado, la importación escribiría en un campo que
        // ningún análisis ve.
        assertThat(campoClinicoRepository.findById(campoId).orElseThrow().getActivo()).isTrue();
        assertThat(cuantosCampos(datasetId, "servicio")).isEqualTo(1);
    }

    // ==================================================================
    // I — la protección manual sigue en pie
    // ==================================================================

    @Test
    void I_crearManualmenteDosCamposConElMismoCodigoSigueFallando() {
        Long datasetId = crearDataset();
        campoClinicoService.crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true));

        assertThatThrownBy(() ->
                campoClinicoService.crear(datasetId, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un campo clínico con el código");
    }

    @Test
    void I_laProteccionTambienAplicaTrasUsarLaOperacionDeImportacion() {
        Long datasetId = crearDataset();
        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        assertThatThrownBy(() ->
                campoClinicoService.crear(datasetId, peticion("servicio", TipoDatoExcel.TEXTO, true, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un campo clínico con el código");
    }

    @Test
    void I_renombrarUnCampoAlCodigoDeOtroSigueFallando() {
        Long datasetId = crearDataset();
        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());
        Long idServicio = campoClinicoRepository
                .findByDatasetIdAndCodigoIgnoreCase(datasetId, "servicio").orElseThrow().getId();

        assertThatThrownBy(() -> campoClinicoService.actualizar(
                datasetId, idServicio, peticion("pacienteCodigo", TipoDatoExcel.TEXTO, true, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe otro campo clínico con el código");
    }

    // ==================================================================
    // J — el dataset fallido queda reutilizable, no a medias
    // ==================================================================

    @Test
    void J_unDatasetCuyaImportacionFalloConservaSusCamposYSiguePudiendoReanudarse() {
        Long datasetId = crearDataset();
        // Primer intento: campos creados, registros no (la importación falló).
        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        assertThat(campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)).hasSize(6);
        assertThat(registroClinicoGenericoRepository.findByDatasetId(datasetId)).isEmpty();

        // Reanudación: no se pierde nada y ahora sí entran los registros.
        campoClinicoService.asegurarParaImportacion(datasetId, columnasDelAsistente());

        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo("P1");
        r.setFechaEvento(LocalDate.of(2026, 1, 1));
        registroClinicoGenericoService.crear(r);

        assertThat(campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId)).hasSize(6);
        assertThat(registroClinicoGenericoRepository.findByDatasetId(datasetId)).hasSize(1);
    }

    @Test
    void asegurarSobreUnDatasetInexistenteFalla() {
        assertThatThrownBy(() ->
                campoClinicoService.asegurarParaImportacion(-1L, columnasDelAsistente()))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
