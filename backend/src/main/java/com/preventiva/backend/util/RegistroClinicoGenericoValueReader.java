package com.preventiva.backend.util;

import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.entity.RegistroClinicoGenerico;
import com.preventiva.backend.enums.TipoDatoExcel;

import java.time.LocalDate;
import java.util.Set;

public class RegistroClinicoGenericoValueReader {

    private static final Set<String> CAMPOS_COMUNES = Set.of(
            "pacienteCodigo", "fechaEvento", "servicio", "tipoEvento",
            "procedimiento", "diagnostico", "edad", "sexo");

    private static final Set<String> VALORES_VERDADEROS = Set.of(
            "SI", "S", "TRUE", "VERDADERO", "1", "X", "POSITIVO", "ADECUADA");

    private static final Set<String> VALORES_FALSOS = Set.of(
            "NO", "N", "FALSE", "FALSO", "0", "NEGATIVO", "INADECUADA", "NO ADECUADA");

    private RegistroClinicoGenericoValueReader() {
    }

    public static boolean esComunReal(CampoClinico campoClinico) {
        return Boolean.TRUE.equals(campoClinico.getEsComun()) && CAMPOS_COMUNES.contains(campoClinico.getCodigo());
    }

    public static Object leerValorCrudo(RegistroClinicoGenerico registro, CampoClinico campoClinico) {
        String codigo = campoClinico.getCodigo();

        if (esComunReal(campoClinico)) {
            return switch (codigo) {
                case "pacienteCodigo" -> registro.getPacienteCodigo();
                case "fechaEvento" -> registro.getFechaEvento();
                case "servicio" -> registro.getServicio();
                case "tipoEvento" -> registro.getTipoEvento();
                case "procedimiento" -> registro.getProcedimiento();
                case "diagnostico" -> registro.getDiagnostico();
                case "edad" -> registro.getEdad();
                case "sexo" -> registro.getSexo();
                default -> null;
            };
        }

        return registro.getDatosDinamicos() != null ? registro.getDatosDinamicos().get(codigo) : null;
    }

    public static Object coercionar(Object valorCrudo, TipoDatoExcel tipoDato) {
        if (valorCrudo == null) {
            return null;
        }

        return switch (tipoDato) {
            case TEXTO -> String.valueOf(valorCrudo);
            case ENTERO -> coercionarEntero(valorCrudo);
            case DECIMAL -> coercionarDecimal(valorCrudo);
            case FECHA -> coercionarFecha(valorCrudo);
            case BOOLEANO -> coercionarBooleano(valorCrudo);
        };
    }

    private static Integer coercionarEntero(Object valor) {
        if (valor instanceof Integer i) {
            return i;
        }

        if (valor instanceof Number n) {
            return n.intValue();
        }

        try {
            return (int) Double.parseDouble(String.valueOf(valor).trim().replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private static Double coercionarDecimal(Object valor) {
        if (valor instanceof Number n) {
            return n.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(valor).trim().replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate coercionarFecha(Object valor) {
        if (valor instanceof LocalDate fecha) {
            return fecha;
        }

        try {
            return LocalDate.parse(String.valueOf(valor).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean coercionarBooleano(Object valor) {
        if (valor instanceof Boolean b) {
            return b;
        }

        String normalizado = TextNormalizer.normalize(String.valueOf(valor));

        if (VALORES_VERDADEROS.contains(normalizado)) {
            return Boolean.TRUE;
        }

        if (VALORES_FALSOS.contains(normalizado)) {
            return Boolean.FALSE;
        }

        return null;
    }
}
