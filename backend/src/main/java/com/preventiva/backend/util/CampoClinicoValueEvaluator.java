package com.preventiva.backend.util;

import com.preventiva.backend.dto.ErrorFilaImportacionGenericaDto;
import com.preventiva.backend.entity.MapeoCampoImportacion;
import com.preventiva.backend.enums.PoliticaCampoFaltante;
import com.preventiva.backend.enums.SeveridadError;
import com.preventiva.backend.enums.TipoDatoExcel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CampoClinicoValueEvaluator {

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ISO_LOCAL_DATE);

    private static final Set<String> VALORES_VERDADEROS = Set.of(
            "SI", "S", "TRUE", "VERDADERO", "1", "X", "POSITIVO", "ADECUADA");

    private static final Set<String> VALORES_FALSOS = Set.of(
            "NO", "N", "FALSE", "FALSO", "0", "NEGATIVO", "INADECUADA", "NO ADECUADA");

    private CampoClinicoValueEvaluator() {
    }

    public static Resultado evaluar(MapeoCampoImportacion mapeo, String valorOriginal, int numeroFila) {
        boolean valorVacio = valorOriginal == null || valorOriginal.isBlank();

        if (valorVacio) {
            return resolverSegunPolitica(mapeo, numeroFila, valorOriginal,
                    "VALOR_OBLIGATORIO_VACIO", "El valor está vacío.");
        }

        TipoDatoExcel tipoDato = mapeo.getTipoDato();

        if (tipoDato == TipoDatoExcel.FECHA && ClinicalValueNormalizer.esPacienteSigueIngresado(valorOriginal)) {
            return resolverSegunPolitica(mapeo, numeroFila, valorOriginal,
                    "PACIENTE_SIGUE_INGRESADO", "El paciente sigue ingresado; no se registra la fecha.");
        }

        if (tipoDato != TipoDatoExcel.TEXTO && ClinicalValueNormalizer.esValorAusenteClinico(valorOriginal)) {
            return resolverSegunPolitica(mapeo, numeroFila, valorOriginal,
                    "VALOR_AUSENTE_CLINICO", "El valor indica ausencia de registro clínico; se importará como vacío.");
        }

        Optional<Object> parseado = parsear(tipoDato, valorOriginal);

        if (parseado.isEmpty()) {
            return resolverSegunPolitica(mapeo, numeroFila, valorOriginal,
                    tipoErrorFormato(tipoDato), mensajeFormato(tipoDato));
        }

        return Resultado.valorValido(parseado.get());
    }

    private static Resultado resolverSegunPolitica(
            MapeoCampoImportacion mapeo, int numeroFila, String valorOriginal, String tipoError, String mensaje) {
        PoliticaCampoFaltante politica = mapeo.getPoliticaCampoFaltante() != null
                ? mapeo.getPoliticaCampoFaltante()
                : PoliticaCampoFaltante.porDefecto(Boolean.TRUE.equals(mapeo.getObligatorio()));

        return switch (politica) {
            case ERROR -> Resultado.conReporte(construirError(
                    mapeo, numeroFila, valorOriginal, tipoError, mensaje, SeveridadError.ERROR));
            case ADVERTENCIA -> Resultado.conReporte(construirError(
                    mapeo, numeroFila, valorOriginal, tipoError, mensaje, SeveridadError.ADVERTENCIA));
            case IGNORAR -> Resultado.ausente();
            case NULO -> Resultado.nuloExplicito();
        };
    }

    private static ErrorFilaImportacionGenericaDto construirError(
            MapeoCampoImportacion mapeo, int numeroFila, String valorOriginal,
            String tipoError, String mensaje, SeveridadError severidad) {
        return ErrorFilaImportacionGenericaDto.builder()
                .numeroFila(numeroFila)
                .nombreColumna(mapeo.getNombreColumnaOrigen())
                .valorOriginal(valorOriginal)
                .tipoError(tipoError)
                .severidad(severidad.name())
                .mensaje(mensaje)
                .build();
    }

    private static Optional<Object> parsear(TipoDatoExcel tipoDato, String valor) {
        return switch (tipoDato) {
            case TEXTO -> Optional.of(valor.trim());
            case ENTERO -> parsearEntero(valor);
            case DECIMAL -> parsearDecimal(valor);
            case FECHA -> parsearFecha(valor);
            case BOOLEANO -> parsearBooleano(valor);
        };
    }

    private static Optional<Object> parsearEntero(String valor) {
        try {
            String limpio = valor.trim().replace(",", ".");
            double numero = Double.parseDouble(limpio);

            if (numero % 1 != 0) {
                return Optional.empty();
            }

            return Optional.of((int) numero);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Object> parsearDecimal(String valor) {
        try {
            String limpio = valor.trim().replace(",", ".");
            return Optional.of(Double.parseDouble(limpio));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Object> parsearFecha(String valor) {
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return Optional.of(LocalDate.parse(valor.trim(), formato));
            } catch (DateTimeParseException ignored) {
                // Probamos el siguiente formato.
            }
        }

        return Optional.empty();
    }

    private static Optional<Object> parsearBooleano(String valor) {
        String normalizado = TextNormalizer.normalize(valor);

        if (VALORES_VERDADEROS.contains(normalizado)) {
            return Optional.of(Boolean.TRUE);
        }

        if (VALORES_FALSOS.contains(normalizado)) {
            return Optional.of(Boolean.FALSE);
        }

        return Optional.empty();
    }

    private static String tipoErrorFormato(TipoDatoExcel tipoDato) {
        return switch (tipoDato) {
            case ENTERO, DECIMAL -> "FORMATO_NUMERO_INVALIDO";
            case FECHA -> "FORMATO_FECHA_INVALIDO";
            case BOOLEANO -> "FORMATO_BOOLEANO_INVALIDO";
            case TEXTO -> "VALOR_NO_RECONOCIDO";
        };
    }

    private static String mensajeFormato(TipoDatoExcel tipoDato) {
        return switch (tipoDato) {
            case ENTERO -> "El valor debe ser un número entero.";
            case DECIMAL -> "El valor debe ser un número decimal válido.";
            case FECHA -> "La fecha no tiene un formato válido.";
            case BOOLEANO -> "El valor debe ser interpretable como Sí/No.";
            case TEXTO -> "Valor no reconocido.";
        };
    }

    public static final class Resultado {
        private final boolean presente;
        private final Object valor;
        private final ErrorFilaImportacionGenericaDto error;

        private Resultado(boolean presente, Object valor, ErrorFilaImportacionGenericaDto error) {
            this.presente = presente;
            this.valor = valor;
            this.error = error;
        }

        static Resultado valorValido(Object valor) {
            return new Resultado(true, valor, null);
        }

        static Resultado ausente() {
            return new Resultado(false, null, null);
        }

        static Resultado nuloExplicito() {
            return new Resultado(true, null, null);
        }

        static Resultado conReporte(ErrorFilaImportacionGenericaDto error) {
            return new Resultado(true, null, error);
        }

        public boolean isPresente() {
            return presente;
        }

        public Object getValor() {
            return valor;
        }

        public ErrorFilaImportacionGenericaDto getError() {
            return error;
        }
    }
}
