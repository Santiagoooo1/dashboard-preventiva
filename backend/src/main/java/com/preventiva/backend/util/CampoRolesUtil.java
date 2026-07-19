package com.preventiva.backend.util;

import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.EnumSet;
import java.util.Set;

public class CampoRolesUtil {

    private static final Set<TipoDatoExcel> AGRUPABLES =
            EnumSet.of(TipoDatoExcel.TEXTO, TipoDatoExcel.BOOLEANO, TipoDatoExcel.ENTERO, TipoDatoExcel.FECHA);

    private static final Set<TipoDatoExcel> NUMERICOS =
            EnumSet.of(TipoDatoExcel.ENTERO, TipoDatoExcel.DECIMAL);

    private CampoRolesUtil() {
    }

    public static boolean esFiltrable(TipoDatoExcel tipoDato) {
        return true;
    }

    public static boolean esAgrupable(TipoDatoExcel tipoDato) {
        return AGRUPABLES.contains(tipoDato);
    }

    public static boolean esNumerico(TipoDatoExcel tipoDato) {
        return NUMERICOS.contains(tipoDato);
    }

    public static boolean esFecha(TipoDatoExcel tipoDato) {
        return tipoDato == TipoDatoExcel.FECHA;
    }
}
