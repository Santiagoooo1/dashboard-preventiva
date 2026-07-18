package com.preventiva.backend.util;

import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.OperadorFiltro;
import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class FiltroMetricaEvaluator {

    private FiltroMetricaEvaluator() {
    }

    public static boolean cumpleTodos(
            RegistroClinicoGenerico registro,
            List<FiltroMetricaDto> filtros,
            Function<String, CampoClinico> resolverCampo) {
        if (filtros == null || filtros.isEmpty()) {
            return true;
        }

        for (FiltroMetricaDto filtro : filtros) {
            CampoClinico campoClinico = resolverCampo.apply(filtro.getCampo());

            if (campoClinico == null) {
                return false;
            }

            Object valorCrudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campoClinico);
            Object valor = RegistroClinicoGenericoValueReader.coercionar(valorCrudo, campoClinico.getTipoDato());

            if (!cumple(filtro, valor, campoClinico.getTipoDato())) {
                return false;
            }
        }

        return true;
    }

    private static boolean cumple(FiltroMetricaDto filtro, Object valor, TipoDatoExcel tipoDato) {
        OperadorFiltro operador = filtro.getOperador();

        return switch (operador) {
            case IS_NULL -> valor == null;
            case NOT_NULL -> valor != null;
            case EQ -> valor != null && iguales(valor, filtro.getValor(), tipoDato);
            case NE -> valor == null || !iguales(valor, filtro.getValor(), tipoDato);
            case IN -> valor != null && perteneceALista(valor, filtro.getValor(), tipoDato);
            case NOT_IN -> valor == null || !perteneceALista(valor, filtro.getValor(), tipoDato);
            case GT -> valor != null && comparar(valor, filtro.getValor(), tipoDato) > 0;
            case GTE -> valor != null && comparar(valor, filtro.getValor(), tipoDato) >= 0;
            case LT -> valor != null && comparar(valor, filtro.getValor(), tipoDato) < 0;
            case LTE -> valor != null && comparar(valor, filtro.getValor(), tipoDato) <= 0;
            case CONTAINS -> valor != null && TextNormalizer.normalize(String.valueOf(valor))
                    .contains(TextNormalizer.normalize(String.valueOf(filtro.getValor())));
        };
    }

    private static boolean iguales(Object valor, Object esperado, TipoDatoExcel tipoDato) {
        if (tipoDato == TipoDatoExcel.TEXTO) {
            return TextNormalizer.normalize(String.valueOf(valor))
                    .equals(TextNormalizer.normalize(String.valueOf(esperado)));
        }

        Object esperadoCoercido = RegistroClinicoGenericoValueReader.coercionar(esperado, tipoDato);
        return valor.equals(esperadoCoercido);
    }

    private static boolean perteneceALista(Object valor, Object listaEsperada, TipoDatoExcel tipoDato) {
        if (!(listaEsperada instanceof Collection<?> coleccion)) {
            return false;
        }

        for (Object esperado : coleccion) {
            if (iguales(valor, esperado, tipoDato)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static int comparar(Object valor, Object esperado, TipoDatoExcel tipoDato) {
        Object esperadoCoercido = RegistroClinicoGenericoValueReader.coercionar(esperado, tipoDato);

        if (esperadoCoercido == null) {
            throw new IllegalArgumentException("El valor de comparación no es válido para el tipo de dato del campo.");
        }

        return ((Comparable<Object>) valor).compareTo(esperadoCoercido);
    }
}
