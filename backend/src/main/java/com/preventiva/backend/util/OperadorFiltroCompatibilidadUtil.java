package com.preventiva.backend.util;

import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OperadorFiltroCompatibilidadUtil {

    private static final Set<TipoDatoExcel> TODOS_LOS_TIPOS = EnumSet.allOf(TipoDatoExcel.class);
    private static final Set<TipoDatoExcel> NUMERICOS_Y_FECHA =
            EnumSet.of(TipoDatoExcel.ENTERO, TipoDatoExcel.DECIMAL, TipoDatoExcel.FECHA);

    private static final Map<OperadorFiltro, Set<TipoDatoExcel>> TIPOS_COMPATIBLES = new EnumMap<>(OperadorFiltro.class);
    private static final Set<OperadorFiltro> REQUIEREN_LISTA = EnumSet.of(OperadorFiltro.IN, OperadorFiltro.NOT_IN);
    private static final Set<OperadorFiltro> NO_REQUIEREN_VALOR = EnumSet.of(OperadorFiltro.IS_NULL, OperadorFiltro.NOT_NULL);

    static {
        TIPOS_COMPATIBLES.put(OperadorFiltro.EQ, TODOS_LOS_TIPOS);
        TIPOS_COMPATIBLES.put(OperadorFiltro.NE, TODOS_LOS_TIPOS);
        TIPOS_COMPATIBLES.put(OperadorFiltro.IN, TODOS_LOS_TIPOS);
        TIPOS_COMPATIBLES.put(OperadorFiltro.NOT_IN, TODOS_LOS_TIPOS);
        TIPOS_COMPATIBLES.put(OperadorFiltro.GT, NUMERICOS_Y_FECHA);
        TIPOS_COMPATIBLES.put(OperadorFiltro.GTE, NUMERICOS_Y_FECHA);
        TIPOS_COMPATIBLES.put(OperadorFiltro.LT, NUMERICOS_Y_FECHA);
        TIPOS_COMPATIBLES.put(OperadorFiltro.LTE, NUMERICOS_Y_FECHA);
        TIPOS_COMPATIBLES.put(OperadorFiltro.IS_NULL, TODOS_LOS_TIPOS);
        TIPOS_COMPATIBLES.put(OperadorFiltro.NOT_NULL, TODOS_LOS_TIPOS);
        TIPOS_COMPATIBLES.put(OperadorFiltro.CONTAINS, EnumSet.of(TipoDatoExcel.TEXTO));
    }

    private OperadorFiltroCompatibilidadUtil() {
    }

    public static List<TipoDatoExcel> tiposCompatibles(OperadorFiltro operador) {
        return TIPOS_COMPATIBLES.get(operador).stream().sorted().toList();
    }

    public static List<OperadorFiltro> operadoresCompatibles(TipoDatoExcel tipoDato) {
        return TIPOS_COMPATIBLES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(tipoDato))
                .map(Map.Entry::getKey)
                .toList();
    }

    public static boolean requiereLista(OperadorFiltro operador) {
        return REQUIEREN_LISTA.contains(operador);
    }

    public static boolean requiereValor(OperadorFiltro operador) {
        return !NO_REQUIEREN_VALOR.contains(operador) && !REQUIEREN_LISTA.contains(operador);
    }
}
