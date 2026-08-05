package com.preventiva.backend.util;

import com.preventiva.backend.enums.RolAnaliticoCampo;
import com.preventiva.backend.enums.TipoDatoExcel;
import com.preventiva.backend.enums.TipoMetrica;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué operaciones admite cada rol analítico y qué exige cada operación
 * (Fase 6.9I.2).
 *
 * <p>Fuente de verdad única: la usan la validación del backend, el catálogo que
 * consume el frontend y el perfil de campos que alimenta al constructor. El
 * frontend oculta lo incompatible por comodidad, pero quien decide es esto —un
 * POST directo con una operación imposible se rechaza igual.
 */
public class OperacionMetricaUtil {

    /**
     * Operaciones garantizadas para CUALQUIER campo activo, sea cual sea su
     * rol. Es lo que asegura que ninguna columna acabe en «no hay operaciones
     * disponibles»: contar registros, contar valores distintos y medir cuánto
     * se rellena son preguntas legítimas incluso sobre texto libre.
     */
    private static final Set<TipoMetrica> OPERACIONES_MINIMAS = EnumSet.of(
            TipoMetrica.CONTEO, TipoMetrica.CONTEO_DISTINTO, TipoMetrica.COMPLETITUD);

    private static final Map<RolAnaliticoCampo, Set<TipoMetrica>> POR_ROL = new EnumMap<>(RolAnaliticoCampo.class);

    static {
        // Sí / No / Sin dato: conteos y porcentajes con filtros, y el reparto completo.
        POR_ROL.put(RolAnaliticoCampo.BOOLEANO, orden(
                TipoMetrica.PORCENTAJE, TipoMetrica.CONTEO, TipoMetrica.DISTRIBUCION,
                TipoMetrica.COMPLETITUD, TipoMetrica.CONTEO_DISTINTO));

        POR_ROL.put(RolAnaliticoCampo.CATEGORICO, orden(
                TipoMetrica.DISTRIBUCION, TipoMetrica.CONTEO, TipoMetrica.PORCENTAJE,
                TipoMetrica.CATEGORIA_PRINCIPAL, TipoMetrica.CONTEO_DISTINTO, TipoMetrica.COMPLETITUD));

        POR_ROL.put(RolAnaliticoCampo.NUMERICO, orden(
                TipoMetrica.PROMEDIO, TipoMetrica.MEDIANA, TipoMetrica.MINIMO, TipoMetrica.MAXIMO,
                TipoMetrica.SUMA, TipoMetrica.CONTEO, TipoMetrica.PORCENTAJE,
                TipoMetrica.CONTEO_DISTINTO, TipoMetrica.COMPLETITUD));

        // Sin DISTRIBUCION: una fecha tiene tantos valores distintos como días,
        // y agruparla por periodo es cosa del widget (serie temporal), no de un
        // reparto categórico con cientos de porciones.
        POR_ROL.put(RolAnaliticoCampo.FECHA, orden(
                TipoMetrica.MINIMO, TipoMetrica.MAXIMO, TipoMetrica.CONTEO,
                TipoMetrica.CONTEO_DISTINTO, TipoMetrica.COMPLETITUD));

        // Sin PROMEDIO, SUMA, DISTRIBUCION ni CATEGORIA_PRINCIPAL: la media de un
        // número de historia clínica no significa nada, y su «categoría más
        // frecuente» es el paciente con más intervenciones disfrazado de KPI.
        POR_ROL.put(RolAnaliticoCampo.IDENTIFICADOR, orden(
                TipoMetrica.CONTEO_DISTINTO, TipoMetrica.CONTEO, TipoMetrica.COMPLETITUD));

        // La distribución se ofrece, pero exigiendo Top N (ver `exigeTopN`).
        POR_ROL.put(RolAnaliticoCampo.TEXTO_LIBRE, orden(
                TipoMetrica.CONTEO_DISTINTO, TipoMetrica.COMPLETITUD, TipoMetrica.CONTEO,
                TipoMetrica.DISTRIBUCION, TipoMetrica.CATEGORIA_PRINCIPAL));
    }

    private OperacionMetricaUtil() {
    }

    /** Operaciones ofrecidas para un rol, en orden de utilidad clínica descendente. */
    public static List<TipoMetrica> operacionesPara(RolAnaliticoCampo rol) {
        Set<TipoMetrica> operaciones = new LinkedHashSet<>(POR_ROL.getOrDefault(rol, Set.of()));
        operaciones.addAll(OPERACIONES_MINIMAS);
        return List.copyOf(operaciones);
    }

    public static boolean esCompatible(TipoMetrica tipo, RolAnaliticoCampo rol) {
        return operacionesPara(rol).contains(tipo);
    }

    /** Las que nunca faltan, para poder afirmar que toda columna produce alguna métrica. */
    public static List<TipoMetrica> operacionesMinimas() {
        return List.copyOf(OPERACIONES_MINIMAS);
    }

    /** Opera sobre un campo concreto (frente a PORCENTAJE/CONTEO, que operan sobre filtros). */
    public static boolean requiereCampoValor(TipoMetrica tipo) {
        return switch (tipo) {
            case PROMEDIO, SUMA, MEDIANA, MINIMO, MAXIMO, CONTEO_DISTINTO, COMPLETITUD -> true;
            default -> false;
        };
    }

    public static boolean requiereCampoAgrupacion(TipoMetrica tipo) {
        return tipo == TipoMetrica.DISTRIBUCION || tipo == TipoMetrica.CATEGORIA_PRINCIPAL;
    }

    public static boolean requiereNumeradorYDenominador(TipoMetrica tipo) {
        return tipo == TipoMetrica.PORCENTAJE;
    }

    /** El resultado se expresa en %, así que la unidad por defecto es «%». */
    public static boolean esPorcentual(TipoMetrica tipo) {
        return tipo == TipoMetrica.PORCENTAJE || tipo == TipoMetrica.COMPLETITUD;
    }

    /**
     * Tipos de dato admisibles como {@code campoValor}. Vacío = cualquiera.
     *
     * <p>Media, mediana y suma exigen números de verdad: interpretar un texto
     * como numérico produciría medias sobre las filas que casualmente parsean.
     */
    public static Set<TipoDatoExcel> tiposDatoValidos(TipoMetrica tipo) {
        return switch (tipo) {
            case PROMEDIO, SUMA, MEDIANA -> EnumSet.of(TipoDatoExcel.ENTERO, TipoDatoExcel.DECIMAL);
            case MINIMO, MAXIMO -> EnumSet.of(TipoDatoExcel.ENTERO, TipoDatoExcel.DECIMAL, TipoDatoExcel.FECHA);
            default -> EnumSet.noneOf(TipoDatoExcel.class);
        };
    }

    /**
     * ¿El resultado es un número que se puede poner en un eje?
     *
     * <p>MINIMO/MAXIMO sobre una fecha devuelven una fecha, no una magnitud: la
     * serie temporal y la comparativa la rechazan porque no hay nada que
     * dibujar.
     */
    public static boolean produceValorNumerico(TipoMetrica tipo, TipoDatoExcel tipoDatoCampoValor) {
        if (tipo == TipoMetrica.DISTRIBUCION || tipo == TipoMetrica.CATEGORIA_PRINCIPAL) {
            return false;
        }
        if ((tipo == TipoMetrica.MINIMO || tipo == TipoMetrica.MAXIMO) && tipoDatoCampoValor == TipoDatoExcel.FECHA) {
            return false;
        }
        return true;
    }

    /**
     * Una distribución sobre un campo de alta cardinalidad necesita Top N: sin
     * recorte serían cientos de barras ilegibles, y en un campo de texto
     * clínico además volcaría el texto completo en la leyenda.
     */
    public static boolean exigeTopN(TipoMetrica tipo, RolAnaliticoCampo rol) {
        return tipo == TipoMetrica.DISTRIBUCION && rol == RolAnaliticoCampo.TEXTO_LIBRE;
    }

    private static Set<TipoMetrica> orden(TipoMetrica... tipos) {
        return new LinkedHashSet<>(List.of(tipos));
    }
}
