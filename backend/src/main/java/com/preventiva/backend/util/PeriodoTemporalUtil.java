package com.preventiva.backend.util;

import com.preventiva.backend.enums.Granularidad;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class PeriodoTemporalUtil {

    private PeriodoTemporalUtil() {
    }

    public record Periodo(String etiqueta, LocalDate fechaInicio, LocalDate fechaFin) {
    }

    public static Periodo calcularPeriodo(LocalDate fecha, Granularidad granularidad) {
        return switch (granularidad) {
            case MES -> {
                YearMonth ym = YearMonth.from(fecha);
                yield new Periodo(ym.toString(), ym.atDay(1), ym.atEndOfMonth());
            }
            case TRIMESTRE -> {
                int trimestre = (fecha.getMonthValue() - 1) / 3 + 1;
                int mesInicio = (trimestre - 1) * 3 + 1;
                LocalDate inicio = LocalDate.of(fecha.getYear(), mesInicio, 1);
                LocalDate fin = inicio.plusMonths(3).minusDays(1);
                yield new Periodo(fecha.getYear() + "-Q" + trimestre, inicio, fin);
            }
            case ANIO -> {
                LocalDate inicio = LocalDate.of(fecha.getYear(), 1, 1);
                LocalDate fin = LocalDate.of(fecha.getYear(), 12, 31);
                yield new Periodo(String.valueOf(fecha.getYear()), inicio, fin);
            }
        };
    }

    public static List<Periodo> generarSecuencia(LocalDate desde, LocalDate hasta, Granularidad granularidad) {
        List<Periodo> resultado = new ArrayList<>();

        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            return resultado;
        }

        Periodo actual = calcularPeriodo(desde, granularidad);

        while (!actual.fechaInicio().isAfter(hasta)) {
            resultado.add(actual);
            actual = calcularPeriodo(actual.fechaFin().plusDays(1), granularidad);
        }

        return resultado;
    }
}
