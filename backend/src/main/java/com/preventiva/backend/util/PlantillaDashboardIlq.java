package com.preventiva.backend.util;

import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.ConfiguracionWidgetDto;
import com.preventiva.backend.dto.FiltroGrupoDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.enums.Granularidad;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoMetrica;
import com.preventiva.backend.enums.TipoResultadoWidget;
import com.preventiva.backend.enums.TipoVisualizacion;
import com.preventiva.backend.enums.TratamientoNulos;

import java.util.List;

/**
 * Definición del dashboard clínico de ILQ, profilaxis y Drago (Fase 6.9I.4).
 *
 * <p>Es la ÚNICA fuente de verdad de las 14 métricas y los 14 widgets: el
 * servicio que aplica la plantilla la lee de aquí, y
 * {@code scripts/demo/verify-dashboard-mvp-ilq.sql} comprueba el resultado
 * contra los datos, sea quien sea quien lo haya creado (la aplicación o el
 * script de demo). Las cifras clínicas no están escritas en ninguno de los dos
 * sitios: se calculan siempre a partir de los registros.
 *
 * <p>Ninguna operación es específica de ILQ: todas son las genéricas del motor
 * (CONTEO, CONTEO_DISTINTO, PORCENTAJE, DISTRIBUCION). Lo clínico está en la
 * configuración, no en el código del motor.
 */
public final class PlantillaDashboardIlq {

    public static final String CODIGO_PANEL = "mvp_ilq_profilaxis_drago";
    public static final String NOMBRE_PANEL = "Vigilancia ILQ, profilaxis antibiótica y Drago";
    public static final String DESCRIPCION_PANEL =
            "Panel clínico mínimo para revisar infección de localización quirúrgica, adecuación de profilaxis "
                    + "antibiótica y registro de la prescripción en Drago.";

    /** Prefijo reservado: la plantilla solo crea o actualiza códigos que empiecen así. */
    public static final String PREFIJO = "mvp_ilq_";

    // --- Códigos de campo que la plantilla necesita encontrar en el dataset ---
    private static final String C_PACIENTE = "pacienteCodigo";
    private static final String C_FECHA = "fechaEvento";
    private static final String C_PROCEDIMIENTO = "procedimiento";
    private static final String C_ILQ = "infeccionLocalizacionQuirurgica";
    private static final String C_LOCALIZACION = "localizacionInfeccion";
    private static final String C_PROFILAXIS_IND = "profilaxisIndicada";
    private static final String C_ADECUACION = "adecuacionProfilaxis";
    private static final String C_MOTIVO = "motivoInadecuacionProfilaxis";
    private static final String C_DRAGO = "prescripcionProfilaxisDrago";

    /**
     * Campos sin los cuales la plantilla no puede aplicarse.
     *
     * <p>Son exactamente los que aparecen en alguna configuración: si falta uno,
     * la métrica que lo usa sería inválida y el dashboard quedaría a medias.
     * Mejor no aplicar nada y decir cuál falta.
     */
    public static final List<String> CAMPOS_ESENCIALES = List.of(
            C_PACIENTE, C_FECHA, C_PROCEDIMIENTO, C_ILQ, C_LOCALIZACION,
            C_PROFILAXIS_IND, C_ADECUACION, C_MOTIVO, C_DRAGO);

    private PlantillaDashboardIlq() {
    }

    /** Una métrica de la plantilla junto con el widget que la muestra. */
    public record ElementoPlantilla(
            String codigo,
            String nombre,
            String descripcion,
            TipoMetrica tipoMetrica,
            ConfiguracionMetricaDto configuracion,
            String unidad,
            int decimales,
            int orden,
            TipoVisualizacion tipoVisualizacion,
            int ancho,
            TipoResultadoWidget tipoResultado,
            ConfiguracionWidgetDto configuracionWidget,
            String tituloWidget) {
    }

    /**
     * Las 14 métricas y sus 14 widgets, en el orden en que se ven.
     *
     * <p>Los ocho primeros son KPI de ancho 3 — cuatro por fila en una rejilla
     * de 12 columnas. Los seis siguientes son los gráficos.
     */
    public static List<ElementoPlantilla> elementos() {
        return List.of(

                // ---------- Fila 1: volumen y resultado ----------

                new ElementoPlantilla(
                        "mvp_ilq_intervenciones", "Intervenciones analizadas",
                        "Número de intervenciones incluidas en el análisis con los filtros activos.",
                        TipoMetrica.CONTEO, config(c -> c.setFiltros(List.of())),
                        null, 0, 1, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                // Pacientes, NO registros: un mismo paciente puede reintervenirse.
                new ElementoPlantilla(
                        "mvp_ilq_pacientes_unicos", "Pacientes únicos",
                        "Pacientes distintos incluidos en el análisis. No coincide con el número de "
                                + "intervenciones: un mismo paciente puede tener más de una.",
                        TipoMetrica.CONTEO_DISTINTO, config(c -> {
                            c.setFiltros(List.of());
                            c.setCampoValor(C_PACIENTE);
                        }),
                        null, 0, 2, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                new ElementoPlantilla(
                        "mvp_ilq_casos", "Casos de ILQ",
                        "Intervenciones con infección de localización quirúrgica documentada como sí.",
                        TipoMetrica.CONTEO, config(c -> c.setFiltros(List.of(filtro(C_ILQ, OperadorFiltro.EQ, true)))),
                        null, 0, 3, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                // El denominador excluye las intervenciones sin ILQ documentada:
                // contarlas como "sin infección" daría una tasa artificialmente baja.
                new ElementoPlantilla(
                        "mvp_ilq_tasa", "Tasa de ILQ",
                        "Porcentaje de infección de localización quirúrgica sobre las intervenciones con ILQ "
                                + "documentada. Las intervenciones sin dato quedan fuera del denominador.",
                        TipoMetrica.PORCENTAJE, config(c -> {
                            c.setFiltros(List.of());
                            c.setNumerador(grupo(filtro(C_ILQ, OperadorFiltro.EQ, true)));
                            c.setDenominador(grupo(filtro(C_ILQ, OperadorFiltro.NOT_NULL, null)));
                            c.setEtiquetaNumerador("Casos de ILQ");
                            c.setEtiquetaDenominador("Intervenciones con ILQ documentada");
                        }),
                        "%", 2, 4, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                // ---------- Fila 2: proceso (profilaxis y Drago) ----------

                // Base: solo donde la profilaxis estaba indicada. Denominador:
                // además, solo donde la adecuación es evaluable — NO_APLICA no es
                // ni bien ni mal, y colarlo abajo hundiría el indicador.
                new ElementoPlantilla(
                        "mvp_ilq_profilaxis_adecuada", "Profilaxis adecuada",
                        "Porcentaje de profilaxis adecuada entre los casos en que estaba indicada y la "
                                + "adecuación es evaluable. Se excluyen los NO_APLICA y los casos sin dato.",
                        TipoMetrica.PORCENTAJE, config(c -> {
                            c.setFiltros(List.of(filtro(C_PROFILAXIS_IND, OperadorFiltro.EQ, true)));
                            c.setNumerador(grupo(filtro(C_ADECUACION, OperadorFiltro.EQ, "ADECUADA")));
                            c.setDenominador(grupo(
                                    filtro(C_ADECUACION, OperadorFiltro.NOT_NULL, null),
                                    filtro(C_ADECUACION, OperadorFiltro.NE, "NO_APLICA")));
                            c.setEtiquetaNumerador("Profilaxis adecuada");
                            c.setEtiquetaDenominador("Casos evaluables");
                        }),
                        "%", 2, 5, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                new ElementoPlantilla(
                        "mvp_ilq_profilaxis_inadecuadas", "Profilaxis inadecuadas",
                        "Número de intervenciones con profilaxis indicada en las que la adecuación fue inadecuada.",
                        TipoMetrica.CONTEO, config(c -> c.setFiltros(List.of(
                                filtro(C_PROFILAXIS_IND, OperadorFiltro.EQ, true),
                                filtro(C_ADECUACION, OperadorFiltro.EQ, "INADECUADA")))),
                        null, 0, 6, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                new ElementoPlantilla(
                        "mvp_ilq_drago_prescripcion", "Prescripción en Drago",
                        "Porcentaje de profilaxis indicadas que constan prescritas en Drago.",
                        TipoMetrica.PORCENTAJE, config(c -> {
                            c.setFiltros(List.of(filtro(C_PROFILAXIS_IND, OperadorFiltro.EQ, true)));
                            c.setNumerador(grupo(filtro(C_DRAGO, OperadorFiltro.EQ, true)));
                            c.setDenominador(grupo(filtro(C_PROFILAXIS_IND, OperadorFiltro.EQ, true)));
                            c.setEtiquetaNumerador("Prescrita en Drago");
                            c.setEtiquetaDenominador("Profilaxis indicadas");
                        }),
                        "%", 2, 7, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                // Mide si el dato CONSTA, con independencia de si es sí o no. Sin
                // este indicador el anterior no es interpretable: un porcentaje
                // bajo podría deberse a que no se prescribe o a que no se
                // registra, y son problemas distintos.
                new ElementoPlantilla(
                        "mvp_ilq_drago_completitud", "Registro de Drago completado",
                        "Porcentaje de profilaxis indicadas con dato registrado en Drago, sea sí o no. "
                                + "Mide la calidad del registro, no la prescripción.",
                        TipoMetrica.PORCENTAJE, config(c -> {
                            c.setFiltros(List.of(filtro(C_PROFILAXIS_IND, OperadorFiltro.EQ, true)));
                            c.setNumerador(grupo(filtro(C_DRAGO, OperadorFiltro.NOT_NULL, null)));
                            c.setDenominador(grupo(filtro(C_PROFILAXIS_IND, OperadorFiltro.EQ, true)));
                            c.setEtiquetaNumerador("Casos con dato en Drago");
                            c.setEtiquetaDenominador("Profilaxis indicadas");
                        }),
                        "%", 2, 8, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null, null),

                // ---------- Gráficos ----------

                // Métrica propia en vez de reutilizar mvp_ilq_tasa: el panel
                // mantiene una relación 1:1 métrica-widget, de modo que editar el
                // KPI no altera la serie ni al revés. Cada mes resuelve su propio
                // numerador y denominador.
                new ElementoPlantilla(
                        "mvp_ilq_tasa_mensual", "Evolución mensual de la tasa de ILQ",
                        "Tasa de ILQ calculada mes a mes. Cada punto usa el numerador y el denominador de su "
                                + "propio mes.",
                        TipoMetrica.PORCENTAJE, config(c -> {
                            c.setFiltros(List.of());
                            c.setNumerador(grupo(filtro(C_ILQ, OperadorFiltro.EQ, true)));
                            c.setDenominador(grupo(filtro(C_ILQ, OperadorFiltro.NOT_NULL, null)));
                            c.setEtiquetaNumerador("Casos de ILQ del mes");
                            c.setEtiquetaDenominador("Intervenciones del mes con ILQ documentada");
                        }),
                        "%", 2, 9, TipoVisualizacion.LINEAS, 12, TipoResultadoWidget.SERIE_TEMPORAL,
                        configWidget(w -> {
                            w.setGranularidad(Granularidad.MES);
                            w.setCampoFecha(C_FECHA);
                        }),
                        "Evolución mensual de la tasa de ILQ"),

                new ElementoPlantilla(
                        "mvp_ilq_localizacion", "Localización de las ILQ",
                        "Reparto de las infecciones de localización quirúrgica según su localización.",
                        TipoMetrica.DISTRIBUCION, config(c -> {
                            c.setCampoAgrupacion(C_LOCALIZACION);
                            c.setFiltros(List.of(
                                    filtro(C_ILQ, OperadorFiltro.EQ, true),
                                    filtro(C_LOCALIZACION, OperadorFiltro.NOT_NULL, null)));
                            c.setTratamientoNulos(TratamientoNulos.EXCLUIR);
                        }),
                        null, 0, 10, TipoVisualizacion.DONUT, 6, TipoResultadoWidget.ACTUAL, null, null),

                new ElementoPlantilla(
                        "mvp_ilq_adecuacion_distribucion", "Resultado de adecuación de profilaxis",
                        "Reparto de las intervenciones según el resultado de la adecuación de la profilaxis.",
                        TipoMetrica.DISTRIBUCION, config(c -> {
                            c.setCampoAgrupacion(C_ADECUACION);
                            c.setFiltros(List.of());
                            c.setTratamientoNulos(TratamientoNulos.EXCLUIR);
                        }),
                        null, 0, 11, TipoVisualizacion.DONUT, 6, TipoResultadoWidget.ACTUAL, null, null),

                // Sin maxCategorias: los nueve motivos se muestran enteros.
                // Agruparlos en "Otros" escondería justo lo que hay que corregir.
                new ElementoPlantilla(
                        "mvp_ilq_motivos_inadecuacion", "Motivos de inadecuación",
                        "Motivos registrados en las profilaxis inadecuadas.",
                        TipoMetrica.DISTRIBUCION, config(c -> {
                            c.setCampoAgrupacion(C_MOTIVO);
                            c.setFiltros(List.of(
                                    filtro(C_ADECUACION, OperadorFiltro.EQ, "INADECUADA"),
                                    filtro(C_MOTIVO, OperadorFiltro.NOT_NULL, null)));
                            c.setTratamientoNulos(TratamientoNulos.EXCLUIR);
                        }),
                        null, 0, 12, TipoVisualizacion.BARRAS, 12, TipoResultadoWidget.ACTUAL, null, null),

                // Aquí sí se incluyen los nulos: "sin documentar" es un estado
                // propio y confundirlo con "no prescrita" cambia el diagnóstico
                // del problema.
                new ElementoPlantilla(
                        "mvp_ilq_drago_distribucion", "Estado de prescripción en Drago",
                        "Reparto de las profilaxis indicadas entre prescritas en Drago, no prescritas y sin "
                                + "documentar.",
                        TipoMetrica.DISTRIBUCION, config(c -> {
                            c.setCampoAgrupacion(C_DRAGO);
                            c.setFiltros(List.of(filtro(C_PROFILAXIS_IND, OperadorFiltro.EQ, true)));
                            c.setTratamientoNulos(TratamientoNulos.INCLUIR_COMO_CATEGORIA);
                        }),
                        null, 0, 13, TipoVisualizacion.DONUT, 6, TipoResultadoWidget.ACTUAL, null, null),

                // La agrupación por procedimiento vive en el widget (COMPARATIVA),
                // no en la métrica: así el mismo conteo puede reagruparse sin
                // duplicar la métrica.
                new ElementoPlantilla(
                        "mvp_ilq_casos_procedimiento", "Casos de ILQ por procedimiento",
                        "Casos de ILQ desglosados por procedimiento quirúrgico.",
                        TipoMetrica.CONTEO, config(c -> c.setFiltros(List.of(filtro(C_ILQ, OperadorFiltro.EQ, true)))),
                        null, 0, 14, TipoVisualizacion.BARRAS, 6, TipoResultadoWidget.COMPARATIVA,
                        configWidget(w -> w.setCampoAgrupacion(C_PROCEDIMIENTO)),
                        null));
    }

    // ------------------------------------------------------------------

    private static ConfiguracionMetricaDto config(java.util.function.Consumer<ConfiguracionMetricaDto> ajustes) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        ajustes.accept(c);
        return c;
    }

    private static ConfiguracionWidgetDto configWidget(java.util.function.Consumer<ConfiguracionWidgetDto> ajustes) {
        ConfiguracionWidgetDto w = new ConfiguracionWidgetDto();
        ajustes.accept(w);
        return w;
    }

    private static FiltroMetricaDto filtro(String campo, OperadorFiltro operador, Object valor) {
        FiltroMetricaDto f = new FiltroMetricaDto();
        f.setCampo(campo);
        f.setOperador(operador);
        f.setValor(valor);
        return f;
    }

    private static FiltroGrupoDto grupo(FiltroMetricaDto... filtros) {
        FiltroGrupoDto g = new FiltroGrupoDto();
        g.setFiltros(List.of(filtros));
        return g;
    }
}
