package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.CampoRecomendadoDto;
import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.ConfiguracionWidgetDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.dto.PerfilCampoDto;
import com.preventiva.backend.dto.PerfilCamposResponseDto;
import com.preventiva.backend.dto.PropuestaDashboardResponseDto;
import com.preventiva.backend.dto.PropuestaWidgetDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.DatasetClinico;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.RolAnaliticoCampo;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.enums.TratamientoNulos;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.service.interfaces.PerfilCampoService;
import com.preventiva.backend.service.interfaces.PlantillaDashboardIlqService;
import com.preventiva.backend.service.interfaces.PropuestaDashboardService;
import com.preventiva.backend.util.PrioridadCampoUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compone el dashboard recomendado de un dataset cualquiera (Fase 6.9I.4.1).
 *
 * <p>Antes, el dashboard inicial recorría las columnas y proponía las que
 * encajaran con unas palabras clave, en el orden en que venían del Excel. Aquí
 * cada columna se puntúa con {@link PrioridadCampoUtil} y las propuestas se
 * ordenan por esa puntuación: el orden NO depende de la posición física de la
 * columna.
 *
 * <p>Devuelve propuestas, no las crea: el usuario las ve, desmarca lo que no
 * quiera y solo entonces se guardan. Y nunca propone un widget por columna —
 * el tope es {@link #MAXIMO_WIDGETS}.
 */
@Service
@RequiredArgsConstructor
public class PropuestaDashboardServiceImpl implements PropuestaDashboardService {

    /** Un dashboard con más de catorce widgets deja de leerse de un vistazo. */
    public static final int MAXIMO_WIDGETS = 14;

    /** Tope de indicadores numéricos (la cabecera del dashboard). */
    public static final int MAXIMO_KPI = 8;

    /** Tope de gráficos: más de seis y el dashboard pide scroll y comparación. */
    public static final int MAXIMO_GRAFICOS = 6;

    /**
     * Una sola evolución temporal. Varias series del mismo dataset cuentan casi
     * lo mismo con distinta fecha y llenan el dashboard sin añadir información.
     */
    public static final int MAXIMO_SERIES_TEMPORALES = 1;

    /**
     * Tope de completitudes. Son útiles —miden si el registro se rellena—, pero
     * media docena convierten un dashboard clínico en un informe de calidad de
     * datos.
     */
    public static final int MAXIMO_COMPLETITUDES = 2;

    /** Con menos de dos columnas activas no hay dashboard que componer. */
    private static final int MINIMO_CAMPOS = 2;

    /** Cuántos filtros y dimensiones se recomiendan como mucho. */
    private static final int MAXIMO_RECOMENDACIONES = 8;

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;
    private final PerfilCampoService perfilCampoService;
    private final PlantillaDashboardIlqService plantillaDashboardIlqService;

    @Override
    @Transactional(readOnly = true)
    public PropuestaDashboardResponseDto proponer(Long datasetId) {
        DatasetClinico dataset = datasetClinicoRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("No existe el dataset con id: " + datasetId));

        List<CampoClinico> campos = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId);
        Map<String, CampoClinico> porCodigo = campos.stream()
                .collect(Collectors.toMap(CampoClinico::getCodigo, Function.identity(), (a, b) -> a));

        PerfilCamposResponseDto perfil = perfilCampoService.obtenerPerfilCampos(datasetId);
        boolean compatibleIlq = Boolean.TRUE.equals(
                plantillaDashboardIlqService.comprobarCompatibilidad(datasetId).getCompatible());

        if (campos.size() < MINIMO_CAMPOS || perfil.getTotalRegistros() == 0) {
            return PropuestaDashboardResponseDto.builder()
                    .datasetId(datasetId)
                    .datasetCodigo(dataset.getCodigo())
                    .suficiente(false)
                    .motivoInsuficiente(perfil.getTotalRegistros() == 0
                            ? "El dataset todavía no tiene registros importados."
                            : "El dataset no tiene columnas suficientes para componer un dashboard.")
                    .propuestas(List.of())
                    .maximoWidgets(MAXIMO_WIDGETS)
                    .compatibleIlq(compatibleIlq)
                    .build();
        }

        List<PropuestaWidgetDto> propuestas = new ArrayList<>();
        // Columnas que ya tienen su widget: evita proponer dos veces lo mismo
        // (la completitud de un identificador que ya se cuenta en distintos, por
        // ejemplo).
        Set<String> camposYaRepresentados = new HashSet<>();

        // ---- 1. Elementos estructurales: van primero pase lo que pase ----
        propuestas.add(totalRegistros());

        // Los estructurales también respetan EXCLUIR: si alguien ha apartado el
        // identificador o la fecha del análisis, no se cuelan por la puerta de
        // atrás por ser "de los que siempre van".
        perfil.getCampos().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEsIdentificadorIndividuo()))
                .filter(c -> !excluido(c, porCodigo))
                .findFirst()
                .ifPresent(c -> {
                    propuestas.add(pacientesUnicos(c));
                    camposYaRepresentados.add(c.getCodigo());
                });

        perfil.getCampos().stream()
                .filter(c -> "FECHA".equals(c.getRolSugerido()))
                .filter(c -> !excluido(c, porCodigo))
                // La fecha más prioritaria, y solo esa: varias evoluciones
                // temporales del mismo dataset son casi el mismo gráfico.
                .max(Comparator.comparingInt((PerfilCampoDto c) -> prioridad(c, porCodigo))
                        .thenComparing(PerfilCampoDto::getCodigo))
                .ifPresent(c -> {
                    propuestas.add(evolucionTemporal(c));
                    camposYaRepresentados.add(c.getCodigo());
                });

        // ---- 2. El resto, por prioridad ----
        // El orden sale de la puntuación, nunca del orden de las columnas.
        List<PerfilCampoDto> ordenados = perfil.getCampos().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getEsIdentificadorIndividuo()))
                .filter(c -> !"FECHA".equals(c.getRolSugerido()))
                .filter(c -> {
                    CampoClinico campo = porCodigo.get(c.getCodigo());
                    return campo != null
                            && PrioridadCampoUtil.mereceWidget(campo, completitud(c));
                })
                .sorted(Comparator
                        .comparingInt((PerfilCampoDto c) -> prioridad(c, porCodigo)).reversed()
                        // Desempate estable por código: dos ejecuciones proponen
                        // lo mismo en el mismo orden.
                        .thenComparing(PerfilCampoDto::getCodigo))
                .toList();

        // Cupos por tipo: sin ellos, un dataset con quince booleanos produce
        // quince tasas y ningún gráfico, y uno con muchas columnas vacías se
        // llena de completitudes.
        Cupos cupos = new Cupos(propuestas);

        for (PerfilCampoDto campoPerfil : ordenados) {
            if (propuestas.size() >= MAXIMO_WIDGETS) break;

            CampoClinico campo = porCodigo.get(campoPerfil.getCodigo());
            if (campo == null) continue;

            // Una columna, un widget: el identificador ya está representado por
            // «Pacientes únicos» y la fecha por la evolución temporal.
            if (camposYaRepresentados.contains(campoPerfil.getCodigo())) continue;

            PropuestaWidgetDto propuesta = propuestaPara(campoPerfil, campo);
            if (propuesta == null) continue;
            if (!cupos.admite(propuesta)) continue;

            cupos.registrar(propuesta);
            camposYaRepresentados.add(campoPerfil.getCodigo());
            propuestas.add(propuesta);
        }

        // ---- 3. Orden final de presentación y anchos ----
        List<PropuestaWidgetDto> finales = ordenarParaPantalla(propuestas);

        return PropuestaDashboardResponseDto.builder()
                .datasetId(datasetId)
                .datasetCodigo(dataset.getCodigo())
                .suficiente(true)
                .propuestas(finales)
                .maximoWidgets(MAXIMO_WIDGETS)
                .compatibleIlq(compatibleIlq)
                .filtrosRecomendados(recomendarFiltros(perfil, porCodigo))
                .dimensionesRecomendadas(recomendarDimensiones(perfil, porCodigo))
                .build();
    }

    // ------------------------------------------------------------------
    // Filtros y dimensiones recomendados
    // ------------------------------------------------------------------

    /**
     * Por qué campos conviene filtrar. Un identificador daría un desplegable con
     * un valor por paciente y el texto libre uno con una observación por fila:
     * ninguno de los dos es una pregunta que nadie le vaya a hacer al panel.
     *
     * <p>Las fechas SÍ entran, aunque no sirvan como dimensión de agrupación
     * categórica: acotar un periodo es el filtro más habitual de todos.
     */
    private List<CampoRecomendadoDto> recomendarFiltros(
            PerfilCamposResponseDto perfil, Map<String, CampoClinico> porCodigo) {

        return perfil.getCampos().stream()
                .filter(c -> {
                    CampoClinico campo = porCodigo.get(c.getCodigo());
                    if (campo == null || excluido(c, porCodigo)) return false;
                    RolAnaliticoCampo rol = rolEfectivo(c);
                    return rol == RolAnaliticoCampo.FECHA
                            || PrioridadCampoUtil.sirveComoDimension(campo, rol, cardinalidadAlta(c));
                })
                .sorted(comparadorPorPrioridad(porCodigo))
                .limit(MAXIMO_RECOMENDACIONES)
                .map(c -> recomendado(c, porCodigo))
                .toList();
    }

    /**
     * Por qué campos conviene agrupar o segmentar. Igual que los filtros pero
     * sin fechas: agrupar por fecha exacta produce una categoría por día.
     */
    private List<CampoRecomendadoDto> recomendarDimensiones(
            PerfilCamposResponseDto perfil, Map<String, CampoClinico> porCodigo) {

        return perfil.getCampos().stream()
                .filter(c -> {
                    CampoClinico campo = porCodigo.get(c.getCodigo());
                    return campo != null
                            && !excluido(c, porCodigo)
                            && PrioridadCampoUtil.sirveComoDimension(
                                    campo, rolEfectivo(c), cardinalidadAlta(c));
                })
                .sorted(comparadorPorPrioridad(porCodigo))
                .limit(MAXIMO_RECOMENDACIONES)
                .map(c -> recomendado(c, porCodigo))
                .toList();
    }

    private CampoRecomendadoDto recomendado(PerfilCampoDto perfil, Map<String, CampoClinico> porCodigo) {
        CampoClinico campo = porCodigo.get(perfil.getCodigo());
        RolAnaliticoCampo rol = rolEfectivo(perfil);

        return CampoRecomendadoDto.builder()
                .codigo(perfil.getCodigo())
                .etiqueta(perfil.getEtiqueta())
                .rol(rol.name())
                .prioridadDashboard(campo.getPrioridadDashboard().name())
                .motivo(PrioridadCampoUtil.motivo(campo, rol, completitud(perfil)))
                .build();
    }

    /** Orden estable: primero la puntuación, y a igualdad, el código. */
    private Comparator<PerfilCampoDto> comparadorPorPrioridad(Map<String, CampoClinico> porCodigo) {
        return Comparator.comparingInt((PerfilCampoDto c) -> prioridad(c, porCodigo)).reversed()
                .thenComparing(PerfilCampoDto::getCodigo);
    }

    private boolean cardinalidadAlta(PerfilCampoDto perfil) {
        return "ALTA".equals(perfil.getCardinalidad());
    }

    // ------------------------------------------------------------------
    // Cupos por tipo de widget
    // ------------------------------------------------------------------

    /**
     * Lleva la cuenta de cuántos widgets de cada clase se han propuesto ya, para
     * que el dashboard salga variado en vez de quince veces lo mismo.
     */
    private static final class Cupos {
        private int kpis;
        private int graficos;
        private int series;
        private int completitudes;

        Cupos(List<PropuestaWidgetDto> yaPropuestos) {
            yaPropuestos.forEach(this::registrar);
        }

        boolean admite(PropuestaWidgetDto p) {
            if (esSerie(p) && series >= MAXIMO_SERIES_TEMPORALES) return false;
            if (esCompletitud(p) && completitudes >= MAXIMO_COMPLETITUDES) return false;
            if (esKpi(p) && kpis >= MAXIMO_KPI) return false;
            return esKpi(p) || graficos < MAXIMO_GRAFICOS;
        }

        void registrar(PropuestaWidgetDto p) {
            if (esSerie(p)) series++;
            if (esCompletitud(p)) completitudes++;
            if (esKpi(p)) kpis++;
            else graficos++;
        }

        private boolean esKpi(PropuestaWidgetDto p) {
            return TipoVisualizacion.KPI.name().equals(p.getTipoVisualizacion());
        }

        private boolean esSerie(PropuestaWidgetDto p) {
            return TipoResultadoWidget.SERIE_TEMPORAL.name().equals(p.getTipoResultado());
        }

        private boolean esCompletitud(PropuestaWidgetDto p) {
            return TipoMetrica.COMPLETITUD.name().equals(p.getTipoMetrica());
        }
    }

    // ------------------------------------------------------------------
    // Elementos estructurales
    // ------------------------------------------------------------------

    private PropuestaWidgetDto totalRegistros() {
        return PropuestaWidgetDto.builder()
                .codigoMetrica("total_registros")
                .nombre("Total de registros")
                .descripcion("Número de registros incluidos en el análisis con los filtros activos.")
                .tipoMetrica(TipoMetrica.CONTEO.name())
                .configuracion(config(c -> c.setFiltros(List.of())))
                .decimales(0)
                .motivo("Volumen del dataset")
                .prioridad(Integer.MAX_VALUE)
                .tipoVisualizacion(TipoVisualizacion.KPI.name())
                .ancho(3)
                .tipoResultado(TipoResultadoWidget.ACTUAL.name())
                .build();
    }

    private PropuestaWidgetDto pacientesUnicos(PerfilCampoDto campo) {
        return PropuestaWidgetDto.builder()
                .codigoMetrica("pacientes_unicos")
                .nombre("Pacientes únicos")
                .descripcion("Pacientes distintos incluidos en el análisis. No coincide con el número de "
                        + "registros: un mismo paciente puede tener más de uno.")
                .tipoMetrica(TipoMetrica.CONTEO_DISTINTO.name())
                .configuracion(config(c -> {
                    c.setFiltros(List.of());
                    c.setCampoValor(campo.getCodigo());
                }))
                .decimales(0)
                .campoOrigen(campo.getCodigo())
                .campoOrigenEtiqueta(campo.getEtiqueta())
                .motivo("Identificador de paciente")
                .prioridad(Integer.MAX_VALUE - 1)
                .tipoVisualizacion(TipoVisualizacion.KPI.name())
                .ancho(3)
                .tipoResultado(TipoResultadoWidget.ACTUAL.name())
                .build();
    }

    private PropuestaWidgetDto evolucionTemporal(PerfilCampoDto campo) {
        ConfiguracionWidgetDto widget = new ConfiguracionWidgetDto();
        widget.setGranularidad(Granularidad.MES);
        widget.setCampoFecha(campo.getCodigo());

        return PropuestaWidgetDto.builder()
                .codigoMetrica("registros_por_mes")
                .nombre("Registros por mes")
                .descripcion("Evolución mensual del número de registros.")
                .tipoMetrica(TipoMetrica.CONTEO.name())
                .configuracion(config(c -> c.setFiltros(List.of())))
                .decimales(0)
                .campoOrigen(campo.getCodigo())
                .campoOrigenEtiqueta(campo.getEtiqueta())
                .motivo("Campo de fecha")
                .prioridad(Integer.MAX_VALUE - 2)
                .tipoVisualizacion(TipoVisualizacion.LINEAS.name())
                .ancho(12)
                .tipoResultado(TipoResultadoWidget.SERIE_TEMPORAL.name())
                .configuracionWidget(widget)
                .build();
    }

    // ------------------------------------------------------------------
    // Una propuesta por columna, según su rol
    // ------------------------------------------------------------------

    /**
     * Qué representación corresponde a cada rol.
     *
     * <p>Nunca una distribución sobre un identificador ni sobre texto libre: en
     * el primer caso sería una barra por paciente y en el segundo volcaría
     * observaciones clínicas en una leyenda.
     */
    private PropuestaWidgetDto propuestaPara(PerfilCampoDto perfil, CampoClinico campo) {
        RolAnaliticoCampo rol = rolEfectivo(perfil);
        int prioridad = prioridad(perfil, campo);
        String motivo = PrioridadCampoUtil.motivo(campo, rol, completitud(perfil));
        boolean casiVacio = completitud(perfil) < PrioridadCampoUtil.COMPLETITUD_MINIMA;

        // Un campo obligatorio pero casi vacío: lo interesante NO es su reparto,
        // es que no se rellena. Se propone su completitud.
        if (casiVacio) {
            return completitud(perfil, campo, prioridad,
                    "Campo obligatorio con muy pocos datos informados");
        }

        return switch (rol) {
            case BOOLEANO -> porcentajeDeSi(perfil, campo, prioridad, motivo);
            case CATEGORICO -> "ALTA".equals(perfil.getCardinalidad())
                    ? distribucionTopN(perfil, campo, prioridad, motivo)
                    : distribucion(perfil, campo, prioridad, motivo);
            case NUMERICO -> media(perfil, campo, prioridad, motivo);
            // Texto libre e identificadores solo admiten completitud; los
            // identificadores además ya están cubiertos por «Pacientes únicos».
            case TEXTO_LIBRE, IDENTIFICADOR -> completitud(perfil, campo, prioridad, motivo);
            // Las fechas se resuelven arriba como evolución temporal.
            case FECHA -> null;
        };
    }

    private PropuestaWidgetDto porcentajeDeSi(
            PerfilCampoDto perfil, CampoClinico campo, int prioridad, String motivo) {
        return base(perfil, campo, prioridad, motivo)
                .codigoMetrica("porcentaje_" + snake(campo.getCodigo()))
                .nombre("% " + campo.getEtiqueta())
                .descripcion("Porcentaje de registros con " + campo.getEtiqueta()
                        + " = Sí, sobre los que tienen dato.")
                .tipoMetrica(TipoMetrica.PORCENTAJE.name())
                .configuracion(config(c -> {
                    c.setFiltros(List.of());
                    c.setNumerador(grupo(filtro(campo.getCodigo(), OperadorFiltro.EQ, true)));
                    c.setDenominador(grupo(filtro(campo.getCodigo(), OperadorFiltro.NOT_NULL, null)));
                    c.setEtiquetaNumerador("Casos con " + campo.getEtiqueta());
                    c.setEtiquetaDenominador("Registros con dato");
                }))
                .unidad("%")
                .decimales(2)
                .tipoVisualizacion(TipoVisualizacion.KPI.name())
                .ancho(3)
                .tipoResultado(TipoResultadoWidget.ACTUAL.name())
                .build();
    }

    private PropuestaWidgetDto distribucion(
            PerfilCampoDto perfil, CampoClinico campo, int prioridad, String motivo) {
        // Donut hasta donde es legible; a partir de ahí, barras.
        boolean donut = perfil.getValoresDistintos() <= 8;

        return base(perfil, campo, prioridad, motivo)
                .codigoMetrica("distribucion_" + snake(campo.getCodigo()))
                .nombre("Distribución por " + campo.getEtiqueta())
                .descripcion("Reparto de los registros según " + campo.getEtiqueta() + ".")
                .tipoMetrica(TipoMetrica.DISTRIBUCION.name())
                .configuracion(config(c -> {
                    c.setCampoAgrupacion(campo.getCodigo());
                    c.setFiltros(List.of());
                    // «Sin dato» como categoría propia: en clínica, que algo no
                    // conste no equivale a que no ocurriera.
                    c.setTratamientoNulos(TratamientoNulos.INCLUIR_COMO_CATEGORIA);
                }))
                .decimales(0)
                .tipoVisualizacion(donut ? TipoVisualizacion.DONUT.name() : TipoVisualizacion.BARRAS.name())
                .ancho(donut ? 6 : 12)
                .tipoResultado(TipoResultadoWidget.ACTUAL.name())
                .build();
    }

    private PropuestaWidgetDto distribucionTopN(
            PerfilCampoDto perfil, CampoClinico campo, int prioridad, String motivo) {
        return base(perfil, campo, prioridad, motivo)
                .codigoMetrica("distribucion_" + snake(campo.getCodigo()))
                .nombre(campo.getEtiqueta() + " más frecuentes")
                .descripcion("Las 10 categorías más frecuentes de " + campo.getEtiqueta()
                        + "; el resto se agrupa en «Otros».")
                .tipoMetrica(TipoMetrica.DISTRIBUCION.name())
                .configuracion(config(c -> {
                    c.setCampoAgrupacion(campo.getCodigo());
                    c.setFiltros(List.of());
                    c.setTratamientoNulos(TratamientoNulos.INCLUIR_COMO_CATEGORIA);
                    c.setMaxCategorias(10);
                }))
                .decimales(0)
                .tipoVisualizacion(TipoVisualizacion.BARRAS.name())
                .ancho(12)
                .tipoResultado(TipoResultadoWidget.ACTUAL.name())
                .advertencia("Este campo tiene " + perfil.getValoresDistintos()
                        + " valores distintos: se muestran los 10 más frecuentes.")
                .build();
    }

    private PropuestaWidgetDto media(
            PerfilCampoDto perfil, CampoClinico campo, int prioridad, String motivo) {
        return base(perfil, campo, prioridad, motivo)
                .codigoMetrica("media_" + snake(campo.getCodigo()))
                .nombre(campo.getEtiqueta() + " (media)")
                .descripcion("Promedio de " + campo.getEtiqueta() + ", excluyendo los registros sin dato.")
                .tipoMetrica(TipoMetrica.PROMEDIO.name())
                .configuracion(config(c -> {
                    c.setFiltros(List.of());
                    c.setCampoValor(campo.getCodigo());
                }))
                .decimales(2)
                .tipoVisualizacion(TipoVisualizacion.KPI.name())
                .ancho(3)
                .tipoResultado(TipoResultadoWidget.ACTUAL.name())
                .build();
    }

    private PropuestaWidgetDto completitud(
            PerfilCampoDto perfil, CampoClinico campo, int prioridad, String motivo) {
        return base(perfil, campo, prioridad, motivo)
                .codigoMetrica("completitud_" + snake(campo.getCodigo()))
                .nombre("Completitud de " + campo.getEtiqueta())
                .descripcion("Porcentaje de registros con " + campo.getEtiqueta()
                        + " informado. Mide la calidad del registro, no el dato en sí.")
                .tipoMetrica(TipoMetrica.COMPLETITUD.name())
                .configuracion(config(c -> {
                    c.setFiltros(List.of());
                    c.setCampoValor(campo.getCodigo());
                }))
                .unidad("%")
                .decimales(2)
                .tipoVisualizacion(TipoVisualizacion.KPI.name())
                .ancho(3)
                .tipoResultado(TipoResultadoWidget.ACTUAL.name())
                .advertencia(perfil.getValoresSinDato() > 0
                        ? "Sin informar en " + perfil.getValoresSinDato() + " de "
                                + perfil.getTotalRegistros() + " registros."
                        : null)
                .build();
    }

    // ------------------------------------------------------------------

    /**
     * Orden de pantalla: primero los KPI (que forman las filas de cabecera) y
     * después los gráficos, conservando dentro de cada grupo el orden de
     * prioridad. Así la primera fila son siempre indicadores y no un gráfico
     * suelto.
     */
    private List<PropuestaWidgetDto> ordenarParaPantalla(List<PropuestaWidgetDto> propuestas) {
        List<PropuestaWidgetDto> kpis = propuestas.stream()
                .filter(p -> TipoVisualizacion.KPI.name().equals(p.getTipoVisualizacion()))
                .toList();
        List<PropuestaWidgetDto> graficos = propuestas.stream()
                .filter(p -> !TipoVisualizacion.KPI.name().equals(p.getTipoVisualizacion()))
                .toList();

        List<PropuestaWidgetDto> finales = new ArrayList<>();
        finales.addAll(kpis);
        finales.addAll(graficos);

        for (int i = 0; i < finales.size(); i++) {
            finales.get(i).setOrden(i + 1);
        }

        return List.copyOf(finales);
    }

    private PropuestaWidgetDto.PropuestaWidgetDtoBuilder base(
            PerfilCampoDto perfil, CampoClinico campo, int prioridad, String motivo) {
        return PropuestaWidgetDto.builder()
                .campoOrigen(campo.getCodigo())
                .campoOrigenEtiqueta(campo.getEtiqueta())
                .motivo(motivo)
                .prioridad(prioridad);
    }

    /** El usuario ha apartado esta columna del análisis automático. */
    private boolean excluido(PerfilCampoDto perfil, Map<String, CampoClinico> porCodigo) {
        CampoClinico campo = porCodigo.get(perfil.getCodigo());
        return campo != null && campo.getPrioridadDashboard() == PrioridadDashboardCampo.EXCLUIR;
    }

    private int prioridad(PerfilCampoDto perfil, Map<String, CampoClinico> porCodigo) {
        CampoClinico campo = porCodigo.get(perfil.getCodigo());
        return campo == null ? 0 : prioridad(perfil, campo);
    }

    private int prioridad(PerfilCampoDto perfil, CampoClinico campo) {
        // Rol EFECTIVO: se puntúa lo que se va a dibujar, no lo que el tipo de
        // dato sugiere en abstracto.
        return PrioridadCampoUtil.puntuar(
                campo,
                rolEfectivo(perfil),
                completitud(perfil),
                "ALTA".equals(perfil.getCardinalidad()));
    }

    /**
     * ¿Prácticamente un valor distinto por registro informado?
     *
     * <p>El umbral absoluto de cardinalidad (25 valores) funciona en datasets
     * grandes, pero en uno pequeño una columna de texto libre puede quedar por
     * debajo y colarse como categoría. La proporción lo detecta con
     * independencia del tamaño.
     *
     * <p>Se exige un mínimo de valores para no descartar un booleano de dos
     * registros o un dataset recién empezado.
     */
    private boolean casiUnicoPorRegistro(PerfilCampoDto perfil) {
        // Solo tiene sentido en texto: que veinte edades o veinte fechas sean
        // distintas es lo normal y no impide calcular su media ni su evolución.
        RolAnaliticoCampo rol = RolAnaliticoCampo.valueOf(perfil.getRolSugerido());
        if (rol != RolAnaliticoCampo.CATEGORICO && rol != RolAnaliticoCampo.TEXTO_LIBRE) {
            return false;
        }

        long informados = perfil.getValoresInformados();
        if (informados < 5) return false;
        return perfil.getValoresDistintos() >= informados * 0.9;
    }

    /**
     * El rol con el que de verdad se va a representar la columna.
     *
     * <p>Un texto con (casi) un valor distinto por registro se clasifica como
     * CATEGORICO mientras no pase del umbral absoluto de cardinalidad, pero no
     * es una categoría: su «distribución» serían veinte barras de altura uno.
     * Aquí se degrada a TEXTO_LIBRE, y eso es lo que se puntúa.
     *
     * <p>Sin esta corrección la aptitud se medía sobre un rol nominal distinto
     * del que se acababa dibujando, y un texto irrepetible marcado FUNDAMENTAL
     * adelantaba a un booleano que sí se puede representar.
     */
    private RolAnaliticoCampo rolEfectivo(PerfilCampoDto perfil) {
        RolAnaliticoCampo rol = RolAnaliticoCampo.valueOf(perfil.getRolSugerido());
        return casiUnicoPorRegistro(perfil) ? RolAnaliticoCampo.TEXTO_LIBRE : rol;
    }

    private double completitud(PerfilCampoDto perfil) {
        return perfil.getCompletitud() != null ? perfil.getCompletitud() : 0.0;
    }

    private String snake(String codigo) {
        return codigo
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase();
    }

    private ConfiguracionMetricaDto config(java.util.function.Consumer<ConfiguracionMetricaDto> ajustes) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        ajustes.accept(c);
        return c;
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
}
