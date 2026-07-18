package com.preventiva.backend.util;

import com.preventiva.backend.dto.ConfiguracionMetricaDto;
import com.preventiva.backend.dto.FiltroMetricaDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.TipoMetrica;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class MetricaCalculoBasico {

    private MetricaCalculoBasico() {
    }

    public static Resultado calcular(
            TipoMetrica tipoMetrica,
            ConfiguracionMetricaDto configuracion,
            List<RegistroClinicoGenerico> registros,
            Function<String, CampoClinico> resolverCampo,
            Integer decimales) {
        return switch (tipoMetrica) {
            case CONTEO -> calcularConteo(configuracion, registros, resolverCampo, decimales);
            case PORCENTAJE -> calcularPorcentaje(configuracion, registros, resolverCampo, decimales);
            case PROMEDIO -> calcularPromedio(configuracion, registros, resolverCampo, decimales);
            case SUMA -> calcularSuma(configuracion, registros, resolverCampo, decimales);
            case DISTRIBUCION -> throw new IllegalArgumentException(
                    "DISTRIBUCION no está soportado por el motor de series/comparativas.");
        };
    }

    private static Resultado calcularConteo(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> registros,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {
        List<FiltroMetricaDto> filtros = filtrosONull(configuracion.getFiltros());

        long total = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtros, resolverCampo))
                .count();

        return Resultado.deValor(redondear((double) total, decimales));
    }

    private static Resultado calcularPorcentaje(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> registros,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {
        List<FiltroMetricaDto> filtrosNum = configuracion.getNumerador() != null
                ? filtrosONull(configuracion.getNumerador().getFiltros()) : List.of();
        List<FiltroMetricaDto> filtrosDen = configuracion.getDenominador() != null
                ? filtrosONull(configuracion.getDenominador().getFiltros()) : List.of();

        long numerador = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosNum, resolverCampo))
                .count();
        long denominador = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtrosDen, resolverCampo))
                .count();

        double valor = denominador == 0 ? 0.0 : (numerador * 100.0) / denominador;

        return Resultado.dePorcentaje(redondear(valor, decimales), numerador, denominador);
    }

    private static Resultado calcularPromedio(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> registros,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {
        List<FiltroMetricaDto> filtros = filtrosONull(configuracion.getFiltros());
        CampoClinico campoValor = resolverCampo.apply(configuracion.getCampoValor());

        if (campoValor == null) {
            return Resultado.deValor(null);
        }

        List<Double> valores = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtros, resolverCampo))
                .map(r -> leerNumero(r, campoValor))
                .filter(Objects::nonNull)
                .toList();

        if (valores.isEmpty()) {
            return Resultado.deValor(null);
        }

        double promedio = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return Resultado.deValor(redondear(promedio, decimales));
    }

    private static Resultado calcularSuma(
            ConfiguracionMetricaDto configuracion, List<RegistroClinicoGenerico> registros,
            Function<String, CampoClinico> resolverCampo, Integer decimales) {
        List<FiltroMetricaDto> filtros = filtrosONull(configuracion.getFiltros());
        CampoClinico campoValor = resolverCampo.apply(configuracion.getCampoValor());

        if (campoValor == null) {
            return Resultado.deValor(0.0);
        }

        double suma = registros.stream()
                .filter(r -> FiltroMetricaEvaluator.cumpleTodos(r, filtros, resolverCampo))
                .map(r -> leerNumero(r, campoValor))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        return Resultado.deValor(redondear(suma, decimales));
    }

    private static Double leerNumero(RegistroClinicoGenerico registro, CampoClinico campoValor) {
        Object valorCrudo = RegistroClinicoGenericoValueReader.leerValorCrudo(registro, campoValor);
        Object valor = RegistroClinicoGenericoValueReader.coercionar(valorCrudo, campoValor.getTipoDato());

        return valor instanceof Number n ? n.doubleValue() : null;
    }

    private static List<FiltroMetricaDto> filtrosONull(List<FiltroMetricaDto> filtros) {
        return filtros != null ? filtros : List.of();
    }

    private static Double redondear(Double valor, Integer decimales) {
        if (valor == null) {
            return null;
        }

        int escala = decimales != null ? decimales : 2;
        return BigDecimal.valueOf(valor).setScale(escala, RoundingMode.HALF_UP).doubleValue();
    }

    public static final class Resultado {
        private final Double valor;
        private final Long totalNumerador;
        private final Long totalDenominador;

        private Resultado(Double valor, Long totalNumerador, Long totalDenominador) {
            this.valor = valor;
            this.totalNumerador = totalNumerador;
            this.totalDenominador = totalDenominador;
        }

        static Resultado deValor(Double valor) {
            return new Resultado(valor, null, null);
        }

        static Resultado dePorcentaje(Double valor, long numerador, long denominador) {
            return new Resultado(valor, numerador, denominador);
        }

        public Double getValor() {
            return valor;
        }

        public Long getTotalNumerador() {
            return totalNumerador;
        }

        public Long getTotalDenominador() {
            return totalDenominador;
        }
    }
}
