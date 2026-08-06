package com.preventiva.backend.util;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.RolAnaliticoCampo;

/**
 * Cuánto merece la pena que una columna aparezca en el dashboard inicial
 * (Fase 6.9I.4.1).
 *
 * <h2>Sobre «fundamental» e «importante»</h2>
 *
 * <p>{@link CampoClinico} NO tiene esos campos: sus únicos atributos son
 * {@code codigo}, {@code etiqueta}, {@code tipoDato}, {@code esComun},
 * {@code obligatorio}, {@code orden} y {@code activo}. Añadir dos banderas
 * nuevas obligaría además a construir la interfaz para marcarlas, y hasta que
 * alguien las marcara todos los datasets existentes valdrían lo mismo.
 *
 * <p>Así que la importancia se DEDUCE de las señales que sí existen y que ya
 * están informadas en todos los datasets:
 *
 * <ul>
 *   <li><b>fundamental</b> ≈ campo común del modelo clínico ({@code esComun}):
 *       paciente, fecha, procedimiento, diagnóstico, edad, sexo. Son los que el
 *       propio esquema reconoce como núcleo, no una opinión.</li>
 *   <li><b>importante</b> ≈ {@code obligatorio}: alguien decidió al definir el
 *       dataset que sin ese dato la fila no vale. Esa es exactamente la
 *       declaración de importancia que se busca.</li>
 *   <li>El resto se ordena por utilidad analítica: rol, cardinalidad y cuánto
 *       está realmente relleno.</li>
 * </ul>
 *
 * <p>La puntuación es determinista y está cubierta por tests: dos ejecuciones
 * sobre el mismo dataset proponen lo mismo, y el orden NO depende de la
 * posición física de la columna en el Excel.
 */
public class PrioridadCampoUtil {

    // --- Pesos. Separados por tramos para que el orden entre categorías sea
    // --- estable aunque se ajusten los detalles dentro de cada una.

    /** Campo común del modelo clínico: el núcleo del dataset. */
    public static final int PESO_FUNDAMENTAL = 1000;

    /** Declarado obligatorio al definir el dataset. */
    public static final int PESO_OBLIGATORIO = 500;

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

        if (Boolean.TRUE.equals(campo.getEsComun())) {
            puntos += PESO_FUNDAMENTAL;
        }

        if (Boolean.TRUE.equals(campo.getObligatorio())) {
            puntos += PESO_OBLIGATORIO;
        }

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
     * <p>Una columna casi vacía se descarta salvo que sea obligatoria: si lo es,
     * su vacío ES el hallazgo y se propone precisamente su completitud.
     */
    public static boolean mereceWidget(CampoClinico campo, double completitud) {
        if (Boolean.TRUE.equals(campo.getObligatorio())) {
            return true;
        }
        return completitud >= COMPLETITUD_MINIMA;
    }

    /** Etiqueta de por qué se propone, para que el usuario pueda decidir. */
    public static String motivo(CampoClinico campo, RolAnaliticoCampo rol) {
        if (Boolean.TRUE.equals(campo.getEsComun()) && Boolean.TRUE.equals(campo.getObligatorio())) {
            return "Campo fundamental y obligatorio del dataset";
        }
        if (Boolean.TRUE.equals(campo.getEsComun())) {
            return "Campo fundamental del modelo clínico";
        }
        if (Boolean.TRUE.equals(campo.getObligatorio())) {
            return "Campo obligatorio del dataset";
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
