package com.preventiva.backend.util;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.RolAnaliticoCampo;

/**
 * Cuánto merece la pena que una columna aparezca en el dashboard inicial.
 *
 * <h2>De dónde sale la importancia (Fase 6.9J.1)</h2>
 *
 * <p>De {@code prioridadDashboard}, un atributo explícito de
 * {@link CampoClinico} que el usuario decide. Antes se APROXIMABA con
 * {@code esComun} («es del modelo común, luego será fundamental») y
 * {@code obligatorio} («si es obligatorio, será importante»), y ninguna de las
 * dos cosas es cierta: hay campos comunes que a un servicio concreto no le
 * dicen nada, y campos opcionales que son justamente el indicador que se
 * vigila.
 *
 * <p>Los tres atributos son ahora independientes: {@code obligatorio} es una
 * regla de calidad del dato, {@code esComun} una propiedad estructural del
 * esquema y {@code prioridadDashboard} una decisión analítica.
 *
 * <p>El resto de la puntuación sigue igual: rol analítico, cardinalidad y
 * cuánto está realmente relleno el campo. Es determinista y está cubierta por
 * tests — dos ejecuciones sobre el mismo dataset proponen lo mismo, y el orden
 * NO depende de la posición física de la columna en el Excel.
 */
public class PrioridadCampoUtil {

    // --- Pesos. Separados por tramos para que el orden entre categorías sea
    // --- estable aunque se ajusten los detalles dentro de cada una.

    /** Marcado FUNDAMENTAL para dashboards. */
    public static final int PESO_FUNDAMENTAL = 1000;

    /** Marcado IMPORTANTE para dashboards. */
    public static final int PESO_IMPORTANTE = 500;

    /** Un booleano clínico es casi siempre el indicador que se quiere ver. */
    public static final int PESO_BOOLEANO = 200;

    /** Una categoría legible en un gráfico. */
    public static final int PESO_CATEGORICO_LEGIBLE = 150;

    /** Una magnitud sobre la que promediar. */
    public static final int PESO_NUMERICO = 120;

    /** Una fecha da la evolución temporal. */
    public static final int PESO_FECHA = 100;

    /** Identifica al individuo: imprescindible, pero para contar, no para repartir. */
    public static final int PESO_IDENTIFICADOR = 90;

    /** Categoría con demasiados valores: solo Top N, y con reservas. */
    public static final int PESO_CATEGORICO_ALTO = 30;

    /** Texto libre: solo completitud. */
    public static final int PESO_TEXTO_LIBRE = 10;

    /**
     * Penalización por columna vacía. Un campo relleno al 5 % produce un
     * indicador que parece clínico y solo mide que nadie lo rellena; que no
     * abra el dashboard.
     */
    public static final int PESO_MAXIMO_COMPLETITUD = 100;

    /** Por debajo de esto, la columna no entra en el dashboard recomendado. */
    public static final double COMPLETITUD_MINIMA = 10.0;

    private PrioridadCampoUtil() {
    }

    /**
     * Puntuación de una columna. Más alta = antes en el dashboard.
     *
     * @param completitud porcentaje de registros con el campo informado (0-100)
     * @param cardinalidadAlta el campo tiene demasiados valores distintos
     */
    public static int puntuar(
            CampoClinico campo, RolAnaliticoCampo rol, double completitud, boolean cardinalidadAlta) {

        int puntos = 0;

        // La relevancia la declara el usuario, no se infiere de cómo se
        // importó el campo.
        puntos += switch (campo.getPrioridadDashboard()) {
            case FUNDAMENTAL -> PESO_FUNDAMENTAL;
            case IMPORTANTE -> PESO_IMPORTANTE;
            case NORMAL, EXCLUIR -> 0;
        };

        puntos += switch (rol) {
            case BOOLEANO -> PESO_BOOLEANO;
            case CATEGORICO -> cardinalidadAlta ? PESO_CATEGORICO_ALTO : PESO_CATEGORICO_LEGIBLE;
            case NUMERICO -> PESO_NUMERICO;
            case FECHA -> PESO_FECHA;
            case IDENTIFICADOR -> PESO_IDENTIFICADOR;
            case TEXTO_LIBRE -> PESO_TEXTO_LIBRE;
        };

        // Proporcional a lo relleno que esté: entre dos campos por lo demás
        // iguales, gana el que tiene datos.
        puntos += (int) Math.round((Math.max(0, Math.min(100, completitud)) / 100.0) * PESO_MAXIMO_COMPLETITUD);

        return puntos;
    }

    /**
     * ¿Merece la pena proponer un widget sobre esta columna?
     *
     * <p>EXCLUIR manda sobre todo lo demás: si alguien ha apartado la columna
     * del análisis, no se propone aunque puntúe alto.
     *
     * <p>Una columna casi vacía se descarta, salvo que sea obligatoria: ahí su
     * vacío ES el hallazgo y se propone precisamente su completitud. Nótese que
     * este sigue siendo un uso legítimo de {@code obligatorio} —habla de la
     * calidad del dato, no de su relevancia analítica.
     */
    public static boolean mereceWidget(CampoClinico campo, double completitud) {
        if (campo.getPrioridadDashboard() == PrioridadDashboardCampo.EXCLUIR) {
            return false;
        }
        if (Boolean.TRUE.equals(campo.getObligatorio())) {
            return true;
        }
        return completitud >= COMPLETITUD_MINIMA;
    }

    /** Etiqueta de por qué se propone, para que el usuario pueda decidir. */
    public static String motivo(CampoClinico campo, RolAnaliticoCampo rol) {
        if (campo.getPrioridadDashboard() == PrioridadDashboardCampo.FUNDAMENTAL) {
            return "Campo marcado como fundamental";
        }
        if (campo.getPrioridadDashboard() == PrioridadDashboardCampo.IMPORTANTE) {
            return "Campo marcado como importante";
        }

        return switch (rol) {
            case BOOLEANO -> "Variable clínica de sí/no";
            case CATEGORICO -> "Variable categórica";
            case NUMERICO -> "Variable numérica";
            case FECHA -> "Campo de fecha";
            case IDENTIFICADOR -> "Identificador de paciente";
            case TEXTO_LIBRE -> "Texto libre";
        };
    }
}
