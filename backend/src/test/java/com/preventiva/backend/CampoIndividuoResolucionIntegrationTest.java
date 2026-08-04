package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.PanelClinicoRequestDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.SubconjuntoPaginaDto;
import com.preventiva.backend.dto.SubconjuntoPerfilDto;
import com.preventiva.backend.dto.SubconjuntoRequestDto;
import com.preventiva.backend.dto.SubconjuntoResumenDto;
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
 * El backend decide qué campo identifica al individuo. El valor que envía el
 * cliente es solo una pista: sin esta comprobación se podía pedir "pacientes
 * únicos" agrupando por sexo o procedimiento y obtener un COUNT DISTINCT sin
 * sentido clínico.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampoIndividuoResolucionIntegrationTest {

    private static final String PREFIJO = "TEST_69H3ID_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private PanelClinicoService panelClinicoService;
    @Autowired private SubconjuntoClinicoService subconjuntoClinicoService;

    private Long panelId;

    private String codigoUnico(String s) {
        return PREFIJO + s + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
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

    /** 3 registros, 2 pacientes (P1 repite), 2 sexos y 2 procedimientos distintos. */
    private void prepararDataset(boolean conIdentificador) {
        DatasetClinicoRequestDto d = new DatasetClinicoRequestDto();
        d.setCodigo(codigoUnico("ID"));
        d.setNombre("Resolución de individuo");
        Long datasetId = datasetClinicoService.crear(d).getId();

        if (conIdentificador) crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "sexo", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "procedimiento", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "cie10", TipoDatoExcel.TEXTO);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA);

        registrar(datasetId, "P1", "MUJER", "COLECISTECTOMIA", "K80", LocalDate.of(2026, 1, 10));
        registrar(datasetId, "P1", "MUJER", "COLECISTECTOMIA", "K80", LocalDate.of(2026, 1, 20));
        registrar(datasetId, "P2", "HOMBRE", "APENDICECTOMIA", "K35", LocalDate.of(2026, 2, 1));

        PanelClinicoRequestDto p = new PanelClinicoRequestDto();
        p.setCodigo(codigoUnico("PANEL"));
        p.setNombre("Panel resolución");
        panelId = panelClinicoService.crear(datasetId, p).getId();
    }

    private void registrar(Long datasetId, String paciente, String sexo, String proc, String cie10, LocalDate fecha) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setSexo(sexo);
        r.setProcedimiento(proc);
        r.setFechaEvento(fecha);
        r.setDatosDinamicos(java.util.Map.of("cie10", cie10));
        registroClinicoGenericoService.crear(r);
    }

    private SubconjuntoRequestDto peticion(String pista) {
        SubconjuntoRequestDto r = new SubconjuntoRequestDto();
        r.setFiltros(List.of());
        r.setCampoIndividuo(pista);
        return r;
    }

    // ---- Caso admitido ----

    @Test
    void pacienteCodigo_seAceptaComoIdentificador() {
        prepararDataset(true);

        SubconjuntoResumenDto resumen = subconjuntoClinicoService.resumen(panelId, peticion("pacienteCodigo"));

        assertThat(resumen.isTienePacientes()).isTrue();
        assertThat(resumen.getCampoIndividuo()).isEqualTo("pacienteCodigo");
        // 3 registros pero 2 individuos: P1 aparece dos veces.
        assertThat(resumen.getTotalRegistros()).isEqualTo(3);
        assertThat(resumen.getTotalPacientesUnicos()).isEqualTo(2);
        assertThat(subconjuntoClinicoService.pacientes(panelId, peticion("pacienteCodigo")).getTotalElementos())
                .isEqualTo(2);
    }

    // ---- Campos que NO identifican al individuo ----

    @Test
    void sexo_noSeAceptaComoIdentificador() {
        prepararDataset(true);

        SubconjuntoResumenDto resumen = subconjuntoClinicoService.resumen(panelId, peticion("sexo"));

        // Sin esta comprobación habría devuelto 2 "pacientes" (MUJER y HOMBRE).
        assertThat(resumen.isTienePacientes()).isFalse();
        assertThat(resumen.getCampoIndividuo()).isNull();
        assertThat(resumen.getTotalPacientesUnicos()).isNull();
        // Los registros siguen disponibles: la degradación es parcial.
        assertThat(resumen.getTotalRegistros()).isEqualTo(3);
    }

    @Test
    void procedimiento_noSeAceptaComoIdentificador() {
        prepararDataset(true);

        SubconjuntoResumenDto resumen = subconjuntoClinicoService.resumen(panelId, peticion("procedimiento"));

        assertThat(resumen.isTienePacientes()).isFalse();
        assertThat(resumen.getTotalPacientesUnicos()).isNull();
        assertThat(resumen.getTotalRegistros()).isEqualTo(3);
    }

    @Test
    void cie10_noSeAceptaComoIdentificador() {
        prepararDataset(true);

        SubconjuntoResumenDto resumen = subconjuntoClinicoService.resumen(panelId, peticion("cie10"));

        assertThat(resumen.isTienePacientes()).isFalse();
        assertThat(resumen.getTotalPacientesUnicos()).isNull();
    }

    @Test
    void campoInexistente_degradaSinErrorTecnico() {
        prepararDataset(true);

        SubconjuntoResumenDto resumen = subconjuntoClinicoService.resumen(panelId, peticion("no_existe_en_absoluto"));

        assertThat(resumen.isTienePacientes()).isFalse();
        assertThat(resumen.getCampoIndividuo()).isNull();
        assertThat(resumen.getTotalRegistros()).isEqualTo(3);
    }

    // ---- Dataset sin ningún identificador ----

    @Test
    void datasetSinIdentificador_mantieneRegistrosYPerfil() {
        prepararDataset(false);

        SubconjuntoResumenDto resumen = subconjuntoClinicoService.resumen(panelId, peticion("pacienteCodigo"));
        assertThat(resumen.isTienePacientes()).isFalse();
        assertThat(resumen.getTotalPacientesUnicos()).isNull();

        // Registros y Perfil siguen operativos.
        SubconjuntoPaginaDto registros = subconjuntoClinicoService.registros(panelId, peticion(null));
        assertThat(registros.getTotalElementos()).isEqualTo(3);
        assertThat(registros.getColumnas()).isNotEmpty();

        SubconjuntoPerfilDto perfil = subconjuntoClinicoService.perfil(panelId, peticion(null));
        assertThat(perfil.getTotalRegistros()).isEqualTo(3);
        assertThat(perfil.getTotalPacientesUnicos()).isNull();

        // La tabla de pacientes sí es un error funcional: no existe la vista.
        assertThatThrownBy(() -> subconjuntoClinicoService.pacientes(panelId, peticion(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Vistas que no dependen del identificador ----

    @Test
    void conPistaInvalida_registrosYPerfilSiguenFuncionando() {
        prepararDataset(true);

        SubconjuntoPaginaDto registros = subconjuntoClinicoService.registros(panelId, peticion("sexo"));
        assertThat(registros.getTotalElementos()).isEqualTo(3);

        SubconjuntoPerfilDto perfil = subconjuntoClinicoService.perfil(panelId, peticion("sexo"));
        assertThat(perfil.getTotalRegistros()).isEqualTo(3);
        // Sin identificador aceptado no se puede afirmar cuántos individuos hay.
        assertThat(perfil.getTotalPacientesUnicos()).isNull();
    }
}
