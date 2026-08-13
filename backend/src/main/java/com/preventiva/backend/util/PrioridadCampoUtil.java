package com.preventiva.backend.util;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.enums.RolAnaliticoCampo;

/**
 * Cuánto merece la pena que una columna aparezca en el dashboard propuesto.
 *
 * <h2>Dos cosas distintas que antes iban juntas (Fase 6.9J.2)</h2>
 *
 * <p><b>A) Relevancia clínica</b> — {@code prioridadDashboard}. La declara quien
 * conoce el uso del dataset. Hasta la Fase 6.9J.1 se aproximaba con
 * {@code esComun} y {@code obligatorio}, que son otra cosa: pertenencia al
 * modelo común y calidad del dato.
 *
 * <p><b>B) Aptitud para representarse</b> — qué tan bien se deja dibujar la
 * columna: su rol analítico, su cardinalidad y cuánto está rellena. Un campo
 * puede ser clínicamente importantísimo y aun así no dar un buen gráfico.
 *
 * <p>Se suman, con pesos elegidos para que <b>la aptitud pueda compensar un
 * escalón de relevancia, pero no dos</b>. Es lo que hace que un FUNDAMENTAL de
 * texto libre con cardinalidad altísima NO gane a un IMPORTANTE booleano —el
 * primero no se puede dibujar—, mientras que a igualdad analítica un
 * FUNDAMENTAL sí gana siempre a un IMPORTANTE.
 *
 * <p>La puntuación es determinista: dos ejecuciones sobre el mismo dataset
 * proponen lo mismo, y el orden NO depende de la posición física de la columna
 * en el Excel.
 */
public class PrioridadCampoUtil {

    // ---------------------------------------------------------------
    // A) Relevancia clínica declarada
    // ---------------------------------------------------------------

    public static final int RELEVANCIA_FUNDAMENTAL = 300;
    public static final int RELEVANCIA_IMPORTANTE = 200;
    public static final int RELEVANCIA_NORMAL = 100;

    /** EXCLUIR no puntúa: ni siquiera entra en la propuesta (ver `mereceWidget`). */
    public static final int RELEVANCIA_EXCLUIDO = 0;

    // ---------------------------------------------------------------
    // B) Aptitud para una visualización
    // ---------------------------------------------------------------

    /** Sí/No: la forma más limpia de una tasa. */
    public static final int APTITUD_BOOLEANO = 140;

    /** Pocas categorías: un donut o unas barras que se leen de un vistazo. */
    public static final int APTITUD_CATEGORICO_BAJO = 130;

    /** Bastantes categorías: legible en barras a ancho completo. */
    public static final int APTITUD_CATEGORICO_MEDIO = 110;

    /** Una magnitud sobre la que promediar. */
    public static final int APTITUD_NUMERICO = 120;

    /** Una fecha da la evolución temporal. */
    public static final int APTITUD_FECHA = 115;

    /** Sirve para contar individuos, no para repartirlos en un gráfico. */
    public static final int APTITUD_IDENTIFICADOR = 60;

    /** Demasiadas categorías: solo Top N, y con reservas. */
    public static final int APTITUD_CATEGORICO_ALTO = 45;

    /** Texto libre: prácticamente solo su completitud dice algo. */
    public static final int APTITUD_TEXTO_LIBRE = 10;

    /**
     * Aporte máximo por estar bien relleno. Entre dos campos por lo demás
     * iguales gana el que tiene datos; un campo al 5 % produce un indicador que
     * parece clínico y solo mide que nadie lo rellena.
     */
    public static final int APTITUD_MAXIMA_COMPLETITUD = 30;

    /** Por debajo de esto, la columna no entra en el dashboard propuesto. */
    public static final double COMPLETITUD_MINIMA = 10.0;

    private PrioridadCampoUtil() {
    }

    /** A) Lo que el usuario ha declarado sobre la relevancia del campo. */
    public static int relevancia(CampoClinico campo) {
        return switch (campo.getPrioridadDashboard()) {
            case FUNDAMENTAL -> RELEVANCIA_FUNDAMENTAL;
            case IMPORTANTE -> RELEVANCIA_IMPORTANTE;
            case NORMAL -> RELEVANCIA_NORMAL;
            case EXCLUIR -> RELEVANCIA_EXCLUIDO;
        };
    }

    /**
     * B) Cuánto se deja representar la columna, con independencia de lo
     * relevante que sea clínicamente.
     */
    public static int aptitud(RolAnaliticoCampo rol, double completitud, boolean cardinalidadAlta) {
        int base = switch (rol) {
            case BOOLEANO -> APTITUD_BOOLEANO;
            case CATEGORICO -> cardinalidadAlta ? APTITUD_CATEGORICO_ALTO : APTITUD_CATEGORICO_BAJO;
            case NUMERICO -> APTITUD_NUMERICO;
            case FECHA -> APTITUD_FECHA;
            case IDENTIFICADOR -> APTITUD_IDENTIFICADOR;
            case TEXTO_LIBRE -> APTITUD_TEXTO_LIBRE;
        };

        double normalizada = Math.max(0, Math.min(100, completitud)) / 100.0;
        return base + (int) Math.round(normalizada * APTITUD_MAXIMA_COMPLETITUD);
    }

    /**
     * Puntuación total de una columna. Más alta = antes en el dashboard.
     *
     * @param completitud porcentaje de registros con el campo informado (0-100)
     * @param cardinalidadAlta el campo tiene demasiados valores distintos
     */
    public static int puntuar(
            CampoClinico campo, RolAnaliticoCampo rol, double completitud, boolean cardinalidadAlta) {
        return relevancia(campo) + aptitud(rol, completitud, cardinalidadAlta);
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

    /**
     * ¿Sirve esta columna como filtro o como dimensión de agrupación?
     *
     * <p>Un identificador produciría un desplegable con un valor por paciente;
     * el texto libre, uno con una observación clínica por fila. Ninguno de los
     * dos es una pregunta que nadie vaya a hacerle a un dashboard.
     */
    public static boolean sirveComoDimension(
            CampoClinico campo, RolAnaliticoCampo rol, boolean cardinalidadAlta) {
        if (campo.getPrioridadDashboard() == PrioridadDashboardCampo.EXCLUIR) {
            return false;
        }

        return switch (rol) {
            case BOOLEANO, NUMERICO -> true;
            // Con cardinalidad extrema deja de ser una dimensión: son etiquetas,
            // no categorías.
            case CATEGORICO -> !cardinalidadAlta;
            case FECHA, IDENTIFICADOR, TEXTO_LIBRE -> false;
        };
    }

    /**
     * Por qué se propone, en lenguaje clínico. Nunca la puntuación: al usuario
     * le sirve saber que es «una variable binaria apta para una tasa», no que
     * ha sacado 340 puntos.
     */
    public static String motivo(CampoClinico campo, RolAnaliticoCampo rol, double completitud) {
        String porRelevancia = switch (campo.getPrioridadDashboard()) {
            case FUNDAMENTAL -> "Campo marcado como fundamental";
            case IMPORTANTE -> "Campo marcado como importante";
            case NORMAL, EXCLUIR -> null;
        };

        String porAptitud = switch (rol) {
            case BOOLEANO -> "variable binaria adecuada para una tasa";
            case CATEGORICO -> "variable categórica adecuada para una distribución";
            case NUMERICO -> "variable numérica con valor de resumen";
            case FECHA -> "campo temporal adecuado para ver la evolución";
            case IDENTIFICADOR -> "identificador de paciente";
            case TEXTO_LIBRE -> "texto libre: solo se mide su completitud";
        };

        if (porRelevancia != null) {
            return porRelevancia + ", " + porAptitud;
        }

        // Sin relevancia declarada, lo que justifica la propuesta es su aptitud;
        // se menciona la cobertura solo cuando de verdad es buena.
        if (completitud >= 90.0) {
            return "Alta completitud y " + porAptitud;
        }

        return porAptitud.substring(0, 1).toUpperCase() + porAptitud.substring(1);
    }
}
