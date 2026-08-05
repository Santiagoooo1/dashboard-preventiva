package com.preventiva.backend;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.MetricaClinicaRequestDto;
import com.preventiva.backend.dto.PerfilCampoDto;
import com.preventiva.backend.dto.PerfilCamposResponseDto;
import com.preventiva.backend.dto.RegistroClinicoGenericoRequestDto;
import com.preventiva.backend.dto.ResultadoMetricaResponseDto;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TratamientoNulos;
import com.preventiva.backend.service.interfaces.CampoClinicoService;
import com.preventiva.backend.service.interfaces.DatasetClinicoService;
import com.preventiva.backend.service.interfaces.MetricaClinicaService;
import com.preventiva.backend.service.interfaces.PerfilCampoService;
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
 * Fase 6.9I.2 — constructor universal de métricas.
 *
 * <p>Comprueba las capacidades genéricas nuevas (conteo distinto, completitud,
 * mediana, mínimo/máximo, categoría principal, porcentaje condicional), el
 * tratamiento de la base vacía y las validaciones que el backend hace por su
 * cuenta, sin fiarse de lo que el frontend haya ocultado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConstructorMetricasIntegrationTest {

    private static final String PREFIJO = "TEST_69I2_";
    private final AtomicInteger contador = new AtomicInteger();

    @Autowired private DatasetClinicoService datasetClinicoService;
    @Autowired private CampoClinicoService campoClinicoService;
    @Autowired private RegistroClinicoGenericoService registroClinicoGenericoService;
    @Autowired private MetricaClinicaService metricaClinicaService;
    @Autowired private PerfilCampoService perfilCampoService;

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private String codigoUnico(String sufijo) {
        return PREFIJO + sufijo + "_" + contador.incrementAndGet() + "_" + System.nanoTime();
    }

    private Long crearDataset() {
        DatasetClinicoRequestDto r = new DatasetClinicoRequestDto();
        r.setCodigo(codigoUnico("DS"));
        r.setNombre("Test 69I2");
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

    private FiltroMetricaDto filtro(String campo, OperadorFiltro operador, Object valor) {
        FiltroMetricaDto f = new FiltroMetricaDto();
        f.setCampo(campo);
        f.setOperador(operador);
        f.setValor(valor);
        return f;
    }

    private FiltroGrupoDto grupo(FiltroMetricaDto... filtros) {
        FiltroGrupoDto g = new FiltroGrupoDto();
        g.setFiltros(List.of(filtros));
        return g;
    }

    private ResultadoMetricaResponseDto ejecutar(Long datasetId, TipoMetrica tipo, ConfiguracionMetricaDto config) {
        MetricaClinicaRequestDto r = new MetricaClinicaRequestDto();
        r.setCodigo(codigoUnico("M"));
        r.setNombre("Métrica de prueba");
        r.setTipoMetrica(tipo);
        r.setConfiguracion(config);
        r.setDecimales(2);

        Long metricaId = metricaClinicaService.crear(datasetId, r).getId();
        return metricaClinicaService.ejecutar(metricaId, null);
    }

    private ConfiguracionMetricaDto configCampoValor(String campo) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setCampoValor(campo);
        return c;
    }

    private ConfiguracionMetricaDto configAgrupacion(String campo) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        c.setCampoAgrupacion(campo);
        return c;
    }

    /**
     * Dataset base: 6 registros, 4 pacientes (P1 repite tres veces).
     *
     * <p>Edades 10, 20, 30, 40, 50 y una sin dato: mediana par (30) distinta de
     * la media (30) solo por poco, pero suficiente para separar ambos cálculos
     * del mínimo y el máximo.
     */
    private Long datasetBase() {
        Long datasetId = crearDataset();
        crearCampo(datasetId, "pacienteCodigo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "fechaEvento", TipoDatoExcel.FECHA, true);
        crearCampo(datasetId, "sexo", TipoDatoExcel.TEXTO, true);
        crearCampo(datasetId, "edad", TipoDatoExcel.ENTERO, true);
        crearCampo(datasetId, "ilq", TipoDatoExcel.BOOLEANO, false);
        crearCampo(datasetId, "adecuacion", TipoDatoExcel.TEXTO, false);

        registro(datasetId, "P1", LocalDate.of(2026, 1, 10), "MUJER", 10, true, "ADECUADA");
        registro(datasetId, "P1", LocalDate.of(2026, 2, 10), "MUJER", 20, false, "ADECUADA");
        registro(datasetId, "P1", LocalDate.of(2026, 3, 10), "MUJER", 30, false, "INADECUADA");
        registro(datasetId, "P2", LocalDate.of(2026, 4, 10), "HOMBRE", 40, false, "NO_APLICA");
        registro(datasetId, "P3", LocalDate.of(2026, 5, 10), "HOMBRE", 50, true, null);
        registro(datasetId, "P4", LocalDate.of(2026, 6, 10), "HOMBRE", null, null, null);

        return datasetId;
    }

    private void registro(
            Long datasetId, String paciente, LocalDate fecha, String sexo, Integer edad,
            Boolean ilq, String adecuacion) {
        RegistroClinicoGenericoRequestDto r = new RegistroClinicoGenericoRequestDto();
        r.setDatasetId(datasetId);
        r.setPacienteCodigo(paciente);
        r.setFechaEvento(fecha);
        r.setSexo(sexo);
        r.setEdad(edad);

        Map<String, Object> dinamicos = new HashMap<>();
        if (ilq != null) dinamicos.put("ilq", ilq ? "SI" : "NO");
        if (adecuacion != null) dinamicos.put("adecuacion", adecuacion);
        r.setDatosDinamicos(dinamicos);

        registroClinicoGenericoService.crear(r);
    }

    // ------------------------------------------------------------------
    // Garantía mínima: toda columna activa produce alguna métrica
    // ------------------------------------------------------------------

    @Test
    void todoCampoActivoOfreceAlMenosUnaOperacion() {
        PerfilCamposResponseDto perfil = perfilCampoService.obtenerPerfilCampos(datasetBase());

        assertThat(perfil.getCampos()).hasSize(6);
        assertThat(perfil.getCampos()).allSatisfy(campo ->
                assertThat(campo.getOperaciones())
                        .as("operaciones de %s", campo.getCodigo())
                        .isNotEmpty());
    }

    @Test
    void todoCampoActivoOfreceLasTresOperacionesMinimas() {
        PerfilCamposResponseDto perfil = perfilCampoService.obtenerPerfilCampos(datasetBase());

        for (PerfilCampoDto campo : perfil.getCampos()) {
            List<String> codigos = campo.getOperaciones().stream().map(o -> o.getCodigo()).toList();
            assertThat(codigos)
                    .as("operaciones mínimas de %s", campo.getCodigo())
                    .contains("CONTEO", "CONTEO_DISTINTO", "COMPLETITUD");
        }
    }

    // ------------------------------------------------------------------
    // Compatibilidad por tipo y rol
    // ------------------------------------------------------------------

    @Test
    void elIdentificadorNoOfrecePromedioNiDistribucion() {
        PerfilCamposResponseDto perfil = perfilCampoService.obtenerPerfilCampos(datasetBase());

        PerfilCampoDto paciente = perfil.getCampos().stream()
                .filter(c -> c.getCodigo().equals("pacienteCodigo"))
                .findFirst().orElseThrow();

        assertThat(paciente.getRolSugerido()).isEqualTo("IDENTIFICADOR");
        assertThat(paciente.getEsIdentificadorIndividuo()).isTrue();
        assertThat(paciente.getOperaciones().stream().map(o -> o.getCodigo()))
                .contains("CONTEO_DISTINTO")
                .doesNotContain("PROMEDIO", "SUMA", "DISTRIBUCION", "CATEGORIA_PRINCIPAL");
    }

    @Test
    void elCampoNumericoOfreceMediaYMediana() {
        PerfilCamposResponseDto perfil = perfilCampoService.obtenerPerfilCampos(datasetBase());

        PerfilCampoDto edad = perfil.getCampos().stream()
                .filter(c -> c.getCodigo().equals("edad"))
                .findFirst().orElseThrow();

        assertThat(edad.getRolSugerido()).isEqualTo("NUMERICO");
        assertThat(edad.getOperaciones().stream().map(o -> o.getCodigo()))
                .contains("PROMEDIO", "MEDIANA", "MINIMO", "MAXIMO", "SUMA");
    }

    @Test
    void elCampoFechaNoOfreceDistribucion() {
        PerfilCamposResponseDto perfil = perfilCampoService.obtenerPerfilCampos(datasetBase());

        PerfilCampoDto fecha = perfil.getCampos().stream()
                .filter(c -> c.getCodigo().equals("fechaEvento"))
                .findFirst().orElseThrow();

        assertThat(fecha.getRolSugerido()).isEqualTo("FECHA");
        assertThat(fecha.getOperaciones().stream().map(o -> o.getCodigo()))
                .contains("MINIMO", "MAXIMO")
                .doesNotContain("DISTRIBUCION", "PROMEDIO");
    }

    // ------------------------------------------------------------------
    // Conteo distinto
    // ------------------------------------------------------------------

    @Test
    void conteoDistintoCuentaIndividuosNoRegistros() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.CONTEO_DISTINTO, configCampoValor("pacienteCodigo"));

        // 6 registros pero 4 pacientes: P1 aparece tres veces.
        assertThat(resultado.getValor()).isEqualTo(4.0);
        assertThat(resultado.getEstado()).isEqualTo("OK");
    }

    @Test
    void conteoDistintoIgnoraLosValoresSinDato() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.CONTEO_DISTINTO, configCampoValor("adecuacion"));

        // ADECUADA, INADECUADA y NO_APLICA; los dos nulos no cuentan.
        assertThat(resultado.getValor()).isEqualTo(3.0);
    }

    // ------------------------------------------------------------------
    // Completitud
    // ------------------------------------------------------------------

    @Test
    void completitudMideElPorcentajeInformado() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.COMPLETITUD, configCampoValor("edad"));

        // 5 de 6 registros tienen edad.
        assertThat(resultado.getValor()).isEqualTo(83.33);
        assertThat(resultado.getTotalNumerador()).isEqualTo(5L);
        assertThat(resultado.getTotalDenominador()).isEqualTo(6L);
        assertThat(resultado.getUnidad()).isEqualTo("%");
    }

    @Test
    void completitudDelCienPorCienEsValida() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.COMPLETITUD, configCampoValor("pacienteCodigo"));

        assertThat(resultado.getValor()).isEqualTo(100.0);
        assertThat(resultado.getEstado()).isEqualTo("OK");
    }

    // ------------------------------------------------------------------
    // Mediana, mínimo y máximo
    // ------------------------------------------------------------------

    @Test
    void medianaConNumeroImparDeValores() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.MEDIANA, configCampoValor("edad"));

        // 10, 20, 30, 40, 50 -> el central es 30.
        assertThat(resultado.getValor()).isEqualTo(30.0);
    }

    @Test
    void medianaConNumeroParDeValoresInterpolaComoPostgres() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = configCampoValor("edad");
        config.setFiltros(List.of(filtro("edad", OperadorFiltro.LTE, 40)));

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.MEDIANA, config);

        // 10, 20, 30, 40 -> (20 + 30) / 2 = 25, igual que percentile_cont(0.5).
        assertThat(resultado.getValor()).isEqualTo(25.0);
    }

    @Test
    void minimoYMaximoSobreCampoNumerico() {
        Long datasetId = datasetBase();

        assertThat(ejecutar(datasetId, TipoMetrica.MINIMO, configCampoValor("edad")).getValor()).isEqualTo(10.0);
        assertThat(ejecutar(datasetId, TipoMetrica.MAXIMO, configCampoValor("edad")).getValor()).isEqualTo(50.0);
    }

    @Test
    void minimoYMaximoSobreFechaDevuelvenTextoNoNumero() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto primera =
                ejecutar(datasetId, TipoMetrica.MINIMO, configCampoValor("fechaEvento"));
        ResultadoMetricaResponseDto ultima =
                ejecutar(datasetId, TipoMetrica.MAXIMO, configCampoValor("fechaEvento"));

        assertThat(primera.getValorTexto()).isEqualTo("2026-01-10");
        assertThat(primera.getValor()).isNull();
        assertThat(ultima.getValorTexto()).isEqualTo("2026-06-10");
    }

    // ------------------------------------------------------------------
    // Porcentaje condicional
    // ------------------------------------------------------------------

    @Test
    void porcentajeCondicionalAplicaLosFiltrosBaseAAmbosLados() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = new ConfiguracionMetricaDto();
        // Base: solo los registros con adecuación evaluable (excluye NO_APLICA y nulos).
        config.setFiltros(List.of(
                filtro("adecuacion", OperadorFiltro.NOT_NULL, null),
                filtro("adecuacion", OperadorFiltro.NE, "NO_APLICA")));
        config.setNumerador(grupo(filtro("adecuacion", OperadorFiltro.EQ, "ADECUADA")));
        config.setDenominador(grupo());
        config.setEtiquetaNumerador("Profilaxis adecuada");
        config.setEtiquetaDenominador("Casos evaluables");

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.PORCENTAJE, config);

        // Base = 3 (dos ADECUADA + una INADECUADA); numerador = 2.
        assertThat(resultado.getTotalNumerador()).isEqualTo(2L);
        assertThat(resultado.getTotalDenominador()).isEqualTo(3L);
        assertThat(resultado.getValor()).isEqualTo(66.67);
        assertThat(resultado.getEtiquetaNumerador()).isEqualTo("Profilaxis adecuada");
        assertThat(resultado.getEtiquetaDenominador()).isEqualTo("Casos evaluables");
    }

    @Test
    void porcentajeDeCeroValidoNoSeConfundeConFaltaDeBase() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = new ConfiguracionMetricaDto();
        config.setNumerador(grupo(filtro("adecuacion", OperadorFiltro.EQ, "NO_EXISTE_ESTE_VALOR")));
        config.setDenominador(grupo());

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.PORCENTAJE, config);

        // Hay 6 registros que evaluar y ninguno cumple: 0 % es un resultado real.
        assertThat(resultado.getValor()).isEqualTo(0.0);
        assertThat(resultado.getEstado()).isEqualTo("OK");
        assertThat(resultado.getTotalDenominador()).isEqualTo(6L);
    }

    @Test
    void porcentajeDelCienPorCienEsValido() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = new ConfiguracionMetricaDto();
        config.setNumerador(grupo(filtro("pacienteCodigo", OperadorFiltro.NOT_NULL, null)));
        config.setDenominador(grupo());

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.PORCENTAJE, config);

        assertThat(resultado.getValor()).isEqualTo(100.0);
        assertThat(resultado.getEstado()).isEqualTo("OK");
    }

    // ------------------------------------------------------------------
    // Denominador cero: nunca NaN, nunca 0 % engañoso
    // ------------------------------------------------------------------

    @Test
    void denominadorCeroDevuelveNullYSinBaseEvaluable() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = new ConfiguracionMetricaDto();
        config.setNumerador(grupo(filtro("adecuacion", OperadorFiltro.EQ, "ADECUADA")));
        // Denominador imposible: ningún registro lo cumple.
        config.setDenominador(grupo(filtro("sexo", OperadorFiltro.EQ, "NO_EXISTE")));

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.PORCENTAJE, config);

        assertThat(resultado.getValor()).isNull();
        assertThat(resultado.getEstado()).isEqualTo("SIN_BASE_EVALUABLE");
        assertThat(resultado.getTotalDenominador()).isZero();
    }

    @Test
    void mediaSinValoresInformadosNoEsCero() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = configCampoValor("edad");
        // Filtro que no deja pasar ningún registro.
        config.setFiltros(List.of(filtro("sexo", OperadorFiltro.EQ, "NO_EXISTE")));

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.PROMEDIO, config);

        assertThat(resultado.getValor()).isNull();
        assertThat(resultado.getEstado()).isEqualTo("SIN_BASE_EVALUABLE");
    }

    @Test
    void medianaSinValoresInformadosNoEsCero() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = configCampoValor("edad");
        config.setFiltros(List.of(filtro("sexo", OperadorFiltro.EQ, "NO_EXISTE")));

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.MEDIANA, config);

        assertThat(resultado.getValor()).isNull();
        assertThat(resultado.getEstado()).isEqualTo("SIN_BASE_EVALUABLE");
    }

    // ------------------------------------------------------------------
    // Nulos y NO_APLICA
    // ------------------------------------------------------------------

    @Test
    void laDistribucionMuestraSinDatoComoCategoriaPropiaPorDefecto() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.DISTRIBUCION, configAgrupacion("adecuacion"));

        // «Sin dato» es una categoría de pleno derecho: en clínica, que algo no
        // conste no equivale a que no ocurriera, y esconderlo falsea el reparto.
        assertThat(resultado.getItems().stream().map(i -> i.getEtiqueta()))
                .containsExactlyInAnyOrder("ADECUADA", "INADECUADA", "NO_APLICA", "Sin dato");
        assertThat(resultado.getItems().stream()
                .filter(i -> i.getEtiqueta().equals("Sin dato"))
                .findFirst().orElseThrow().getValor()).isEqualTo(2L);
    }

    @Test
    void laDistribucionPuedeExcluirLosNulosSiSePideExplicitamente() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = configAgrupacion("adecuacion");
        config.setTratamientoNulos(TratamientoNulos.EXCLUIR);

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.DISTRIBUCION, config);

        assertThat(resultado.getItems().stream().map(i -> i.getEtiqueta()))
                .containsExactlyInAnyOrder("ADECUADA", "INADECUADA", "NO_APLICA")
                .doesNotContain("Sin dato");
    }

    @Test
    void unBooleanoSeRepartecomoSiNoYSinDatoNoComoTrueFalse() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.DISTRIBUCION, configAgrupacion("ilq"));

        assertThat(resultado.getItems().stream().map(i -> i.getEtiqueta()))
                .containsExactlyInAnyOrder("Sí", "No", "Sin dato")
                .doesNotContain("true", "false");
    }

    @Test
    void noAplicaNoSeConfundeConSinDato() {
        Long datasetId = datasetBase();

        // NO_APLICA es un valor real y cuenta como informado; el null, no.
        ResultadoMetricaResponseDto completitud =
                ejecutar(datasetId, TipoMetrica.COMPLETITUD, configCampoValor("adecuacion"));

        assertThat(completitud.getTotalNumerador()).isEqualTo(4L);
        assertThat(completitud.getTotalDenominador()).isEqualTo(6L);
    }

    // ------------------------------------------------------------------
    // Categoría principal
    // ------------------------------------------------------------------

    @Test
    void categoriaPrincipalDevuelveEtiquetaYFrecuencia() {
        Long datasetId = datasetBase();

        ResultadoMetricaResponseDto resultado =
                ejecutar(datasetId, TipoMetrica.CATEGORIA_PRINCIPAL, configAgrupacion("sexo"));

        // 3 HOMBRE frente a 3 MUJER: empate resuelto de forma estable.
        assertThat(resultado.getValorTexto()).isNotNull();
        assertThat(resultado.getTotalNumerador()).isEqualTo(3L);
        assertThat(resultado.getTotalDenominador()).isEqualTo(6L);
        assertThat(resultado.getValor()).isEqualTo(50.0);
    }

    // ------------------------------------------------------------------
    // Top N
    // ------------------------------------------------------------------

    @Test
    void elTopNAgrupaElRestoBajoOtros() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = configAgrupacion("adecuacion");
        config.setMaxCategorias(1);

        ResultadoMetricaResponseDto resultado = ejecutar(datasetId, TipoMetrica.DISTRIBUCION, config);

        // ADECUADA (2) se queda; INADECUADA, NO_APLICA y los dos «Sin dato»
        // caen en «Otros» = 4.
        assertThat(resultado.getItems()).hasSize(2);
        assertThat(resultado.getItems().get(0).getEtiqueta()).isEqualTo("ADECUADA");
        assertThat(resultado.getItems().get(1).getEtiqueta()).isEqualTo("Otros");
        assertThat(resultado.getItems().get(1).getValor()).isEqualTo(4L);
    }

    // ------------------------------------------------------------------
    // Validación del backend (no se fía de lo que oculte el frontend)
    // ------------------------------------------------------------------

    @Test
    void rechazaUnCampoQueNoExisteEnElDataset() {
        Long datasetId = datasetBase();

        assertThatThrownBy(() -> ejecutar(datasetId, TipoMetrica.PROMEDIO, configCampoValor("campo_inventado")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe o no está activo");
    }

    @Test
    void rechazaUnCampoDesactivado() {
        Long datasetId = datasetBase();

        Long campoId = campoClinicoService.listarPorDataset(datasetId).stream()
                .filter(c -> c.getCodigo().equals("edad"))
                .findFirst().orElseThrow().getId();
        campoClinicoService.desactivar(datasetId, campoId);

        assertThatThrownBy(() -> ejecutar(datasetId, TipoMetrica.PROMEDIO, configCampoValor("edad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe o no está activo");
    }

    @Test
    void rechazaUnaMediaSobreUnCampoDeTexto() {
        Long datasetId = datasetBase();

        assertThatThrownBy(() -> ejecutar(datasetId, TipoMetrica.PROMEDIO, configCampoValor("sexo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXTO");
    }

    @Test
    void rechazaUnaMedianaSobreUnaFecha() {
        Long datasetId = datasetBase();

        assertThatThrownBy(() -> ejecutar(datasetId, TipoMetrica.MEDIANA, configCampoValor("fechaEvento")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FECHA");
    }

    @Test
    void rechazaUnaDistribucionSobreElIdentificadorDePaciente() {
        Long datasetId = datasetBase();

        assertThatThrownBy(() ->
                ejecutar(datasetId, TipoMetrica.DISTRIBUCION, configAgrupacion("pacienteCodigo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifica al paciente");
    }

    @Test
    void rechazaUnFiltroConOperadorIncompatibleConElTipo() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = configCampoValor("edad");
        config.setFiltros(List.of(filtro("sexo", OperadorFiltro.GT, "A")));

        assertThatThrownBy(() -> ejecutar(datasetId, TipoMetrica.PROMEDIO, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GT");
    }

    @Test
    void rechazaUnaOperacionSinSuCampoObligatorio() {
        Long datasetId = datasetBase();

        assertThatThrownBy(() ->
                ejecutar(datasetId, TipoMetrica.CONTEO_DISTINTO, new ConfiguracionMetricaDto()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campoValor");
    }

    @Test
    void rechazaUnCodigoDuplicadoEnElMismoDataset() {
        Long datasetId = datasetBase();

        MetricaClinicaRequestDto r = new MetricaClinicaRequestDto();
        r.setCodigo("edad_promedio_69i2");
        r.setNombre("Edad media");
        r.setTipoMetrica(TipoMetrica.PROMEDIO);
        r.setConfiguracion(configCampoValor("edad"));

        metricaClinicaService.crear(datasetId, r);

        assertThatThrownBy(() -> metricaClinicaService.crear(datasetId, r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe una métrica activa con el código");
    }

    @Test
    void rechazaUnTopNNoPositivo() {
        Long datasetId = datasetBase();

        ConfiguracionMetricaDto config = configAgrupacion("adecuacion");
        config.setMaxCategorias(0);

        assertThatThrownBy(() -> ejecutar(datasetId, TipoMetrica.DISTRIBUCION, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor que cero");
    }

    // ------------------------------------------------------------------
    // Convivencia con el resto del producto
    // ------------------------------------------------------------------

    @Test
    void lasMetricasNuevasRespetanLosFiltrosGlobales() {
        Long datasetId = datasetBase();

        MetricaClinicaRequestDto r = new MetricaClinicaRequestDto();
        r.setCodigo(codigoUnico("GLOBAL"));
        r.setNombre("Pacientes distintos");
        r.setTipoMetrica(TipoMetrica.CONTEO_DISTINTO);
        r.setConfiguracion(configCampoValor("pacienteCodigo"));
        Long metricaId = metricaClinicaService.crear(datasetId, r).getId();

        com.preventiva.backend.dto.EjecucionMetricaRequestDto peticion =
                new com.preventiva.backend.dto.EjecucionMetricaRequestDto();
        peticion.setFiltrosGlobales(List.of(filtro("sexo", OperadorFiltro.EQ, "MUJER")));

        ResultadoMetricaResponseDto filtrado = metricaClinicaService.ejecutar(metricaId, peticion);

        // Solo P1 es mujer (con tres registros): un único paciente distinto.
        assertThat(filtrado.getValor()).isEqualTo(1.0);
        assertThat(metricaClinicaService.ejecutar(metricaId, null).getValor()).isEqualTo(4.0);
    }

    @Test
    void elPerfilNoDevuelveValoresDeEjemploDeCamposIdentificadores() {
        PerfilCamposResponseDto perfil = perfilCampoService.obtenerPerfilCampos(datasetBase());

        PerfilCampoDto paciente = perfil.getCampos().stream()
                .filter(c -> c.getCodigo().equals("pacienteCodigo"))
                .findFirst().orElseThrow();

        // Un identificador no se enseña como lista de valores: son datos de paciente.
        assertThat(paciente.getValoresEjemplo()).isEmpty();
    }
}
