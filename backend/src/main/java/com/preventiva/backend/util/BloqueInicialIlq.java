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
import java.util.function.Consumer;

/**
 * Los seis indicadores que un servicio de medicina preventiva quiere ver
 * primero cuando el dataset habla de infección quirúrgica (Fase 6.9L).
 *
 * <p>Nace de una petición concreta: el dashboard automático abría con «Total de
 * registros», «Pacientes únicos» y «Edad media» —ciertos, pero no es lo que se
 * vigila— y «Localización de la infección» no llegaba a aparecer. Aquí se fija
 * qué va delante y por qué.
 *
 * <p>No es una plantilla alternativa a {@link PlantillaDashboardIlq}: son las
 * mismas definiciones clínicas (mismos filtros, mismos numeradores y
 * denominadores), reducidas a las que abren el dashboard inicial y con
 * códigos propios para poder convivir con ella en el mismo dataset.
 *
 * <p>La diferencia importante frente a la plantilla es que allí los campos son
 * todo-o-nada: si falta uno, no se aplica nada. Aquí cada elemento declara qué
 * campos necesita, así que un dataset sin motivos de inadecuación pierde ese
 * widget y conserva los otros cuatro.
 *
 * <p>Ninguna operación es específica de ILQ: todas son las genéricas del motor
 * (PORCENTAJE con numerador/denominador propios, DISTRIBUCION con filtros
 * base). Lo específico es la configuración, que es donde debe estar.
 */
public final class BloqueInicialIlq {

    /** Prefijo propio, para no chocar con {@code mvp_ilq_} de la plantilla completa. */
    public static final String PREFIJO = "ilq_inicial_";

    private static final String C_FECHA = "fechaEvento";
    private static final String C_ILQ = "infeccionLocalizacionQuirurgica";
    private static final String C_LOCALIZACION = "localizacionInfeccion";
    private static final String C_ADECUACION = "adecuacionProfilaxis";
    private static final String C_MOTIVO = "motivoInadecuacionProfilaxis";

    /**
     * Sin este campo el dataset no trata de infección quirúrgica y no se propone
     * nada: los widgets de profilaxis, solos, no son el bloque que se pidió.
     */
    public static final String CAMPO_IMPRESCINDIBLE = C_ILQ;

    /**
     * Valores que significan «la profilaxis no fue adecuada».
     *
     * <p>Hay más de uno porque los ficheros reales no coinciden: unos escriben
     * ADECUADA/INADECUADA y el Excel de trauma que motivó esta corrección usa
     * SI/NO. Con un único valor esperado, «Motivos de inadecuación» salía vacío
     * en la mitad de los hospitales sin decir por qué.
     *
     * <p>No incluye NO_APLICA: una intervención que no requería profilaxis no
     * es una profilaxis mal puesta.
     */
    private static final List<String> VALORES_INADECUADA =
            List.of("INADECUADA", "NO", "N", "FALSE", "0");

    private BloqueInicialIlq() {
    }

    /**
     * Un indicador prioritario junto con el widget que lo muestra y los campos
     * clínicos sin los cuales no puede calcularse.
     */
    public record ElementoInicial(
            String codigo,
            String nombre,
            String descripcion,
            TipoMetrica tipoMetrica,
            ConfiguracionMetricaDto configuracion,
            String unidad,
            Integer decimales,
            TipoVisualizacion tipoVisualizacion,
            int ancho,
            TipoResultadoWidget tipoResultado,
            ConfiguracionWidgetDto configuracionWidget,
            /** Todos deben existir en el dataset; si falta uno, el elemento se omite. */
            List<String> camposNecesarios,
            /** Motivo clínico de la propuesta, para que el usuario sepa por qué está ahí. */
            String motivo) {
    }

    /**
     * Los seis, en el orden en que el médico los pidió: primero cuántos casos
     * hay y sobre cuántos, después dónde se localizan, luego la profilaxis y
     * sus fallos, y al final la evolución.
     */
    public static List<ElementoInicial> elementos() {
        return List.of(

                // 1. Cuántos casos hay. Es lo primero que se pregunta un
                // servicio de preventiva, antes que cualquier proporción: «este
                // trimestre, ¿cuántas infecciones hemos tenido?». Un porcentaje
                // sin el número detrás no permite decidir si hay que actuar.
                new ElementoInicial(
                        PREFIJO + "casos", "Casos de ILQ",
                        "Número de intervenciones con infección de localización quirúrgica.",
                        TipoMetrica.CONTEO, config(c ->
                                c.setFiltros(List.of(filtro(C_ILQ, OperadorFiltro.EQ, true)))),
                        null, 0, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null,
                        List.of(C_ILQ),
                        "Recuento de infecciones"),

                // 2. Y sobre cuántas. El indicador que resume el dataset entero.
                //
                // El denominador son las intervenciones con ILQ documentada, no
                // todas: un registro sin la casilla rellena no es una operación
                // sin infección, es una operación de la que no se sabe. Contarla
                // como sana diluye la tasa hacia abajo.
                new ElementoInicial(
                        PREFIJO + "tasa", "Tasa de ILQ",
                        "Porcentaje de infección sobre las intervenciones con el dato documentado.",
                        TipoMetrica.PORCENTAJE, config(c -> {
                            c.setFiltros(List.of());
                            c.setNumerador(grupo(filtro(C_ILQ, OperadorFiltro.EQ, true)));
                            c.setDenominador(grupo(filtro(C_ILQ, OperadorFiltro.NOT_NULL, null)));
                            c.setEtiquetaNumerador("Casos de ILQ");
                            c.setEtiquetaDenominador("Intervenciones con ILQ documentada");
                        }),
                        "%", 2, TipoVisualizacion.KPI, 3, TipoResultadoWidget.ACTUAL, null,
                        List.of(C_ILQ),
                        "Indicador principal de vigilancia"),

                // 3. Dónde se infectan.
                //
                // El filtro por ILQ = true es lo que hace útil este gráfico. Sin
                // él, la categoría dominante serían los nulos de todos los
                // pacientes que no se infectaron, y el reparto real quedaría
                // aplastado en una esquina.
                new ElementoInicial(
                        PREFIJO + "localizacion", "Localización de la infección",
                        "Reparto de las infecciones según su localización, solo entre los casos con ILQ.",
                        TipoMetrica.DISTRIBUCION, config(c -> {
                            c.setCampoAgrupacion(C_LOCALIZACION);
                            c.setFiltros(List.of(
                                    filtro(C_ILQ, OperadorFiltro.EQ, true),
                                    filtro(C_LOCALIZACION, OperadorFiltro.NOT_NULL, null)));
                            c.setTratamientoNulos(TratamientoNulos.EXCLUIR);
                        }),
                        null, null, TipoVisualizacion.BARRAS, 6, TipoResultadoWidget.ACTUAL, null,
                        List.of(C_ILQ, C_LOCALIZACION),
                        "Desglose clínico de los casos de infección"),

                // 4. Cómo se está profilactizando.
                //
                // Se muestran todas las categorías tal cual: NO_APLICA es un
                // resultado legítimo —la intervención no requería profilaxis— y
                // sumarlo a INADECUADA inventaría un problema que no existe.
                new ElementoInicial(
                        PREFIJO + "adecuacion", "Adecuación de profilaxis",
                        "Reparto de las intervenciones según el resultado de la adecuación de la profilaxis.",
                        TipoMetrica.DISTRIBUCION, config(c -> {
                            c.setCampoAgrupacion(C_ADECUACION);
                            c.setFiltros(List.of());
                            c.setTratamientoNulos(TratamientoNulos.EXCLUIR);
                        }),
                        null, null, TipoVisualizacion.DONUT, 6, TipoResultadoWidget.ACTUAL, null,
                        List.of(C_ADECUACION),
                        "Resultado de la profilaxis antibiótica"),

                // 5. Por qué falla cuando falla.
                //
                // Solo los casos inadecuados: un motivo anotado en una profilaxis
                // correcta no es un fallo que corregir. Barras y sin recorte de
                // categorías, porque agrupar la cola en «Otros» escondería justo
                // lo que hay que arreglar.
                new ElementoInicial(
                        PREFIJO + "motivos_inadecuacion", "Motivos de inadecuación",
                        "Motivos registrados en las profilaxis clasificadas como inadecuadas.",
                        TipoMetrica.DISTRIBUCION, config(c -> {
                            c.setCampoAgrupacion(C_MOTIVO);
                            c.setFiltros(List.of(
                                    filtro(C_ADECUACION, OperadorFiltro.IN, VALORES_INADECUADA),
                                    filtro(C_MOTIVO, OperadorFiltro.NOT_NULL, null)));
                            c.setTratamientoNulos(TratamientoNulos.EXCLUIR);
                        }),
                        null, null, TipoVisualizacion.BARRAS, 12, TipoResultadoWidget.ACTUAL, null,
                        List.of(C_ADECUACION, C_MOTIVO),
                        "Dónde corregir la práctica de profilaxis"),

                // 6. Cómo evoluciona.
                //
                // Tasa mensual, no recuento. Un mes con 3 casos sobre 100
                // intervenciones y otro con 2 sobre 50 dibujan una línea que baja
                // si se cuentan casos, y que sube —que es lo que pasa de verdad—
                // si se calcula la tasa. Cada punto resuelve su propio numerador
                // y su propio denominador dentro del mes.
                new ElementoInicial(
                        PREFIJO + "tasa_mensual", "Evolución mensual de infección de localización quirúrgica",
                        "Tasa de ILQ calculada mes a mes. Cada punto usa el numerador y el denominador de su "
                                + "propio mes.",
                        TipoMetrica.PORCENTAJE, config(c -> {
                            c.setFiltros(List.of());
                            c.setNumerador(grupo(filtro(C_ILQ, OperadorFiltro.EQ, true)));
                            c.setDenominador(grupo(filtro(C_ILQ, OperadorFiltro.NOT_NULL, null)));
                            c.setEtiquetaNumerador("Casos de ILQ del mes");
                            c.setEtiquetaDenominador("Intervenciones del mes con ILQ documentada");
                        }),
                        "%", 2, TipoVisualizacion.LINEAS, 12, TipoResultadoWidget.SERIE_TEMPORAL,
                        configWidget(w -> {
                            w.setGranularidad(Granularidad.MES);
                            w.setCampoFecha(C_FECHA);
                        }),
                        List.of(C_ILQ, C_FECHA),
                        "Tendencia de la infección quirúrgica"));
    }

    // ------------------------------------------------------------------

    private static ConfiguracionMetricaDto config(Consumer<ConfiguracionMetricaDto> ajustes) {
        ConfiguracionMetricaDto c = new ConfiguracionMetricaDto();
        ajustes.accept(c);
        return c;
    }

    private static ConfiguracionWidgetDto configWidget(Consumer<ConfiguracionWidgetDto> ajustes) {
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
