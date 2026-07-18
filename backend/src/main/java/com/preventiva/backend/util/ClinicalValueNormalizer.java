package com.preventiva.backend.util;

import com.preventiva.backend.enums.CampoDestino;

import java.util.Set;
import java.util.regex.Pattern;

public class ClinicalValueNormalizer {

    private static final Set<String> VALORES_AUSENCIA_CLINICA = Set.of(
            "FALTA DE REGISTRO",
            "SIN REGISTRO",
            "NO HAY REGISTRO",
            "NO REGISTRADO",
            "NO CONSTA",
            "DESCONOCIDO",
            "N/A",
            "NA");

    private static final Set<String> VALORES_AUSENCIA_TEXTO_LIBRE = Set.of(
            "N/A",
            "NA");

    private static final String GUION_AUSENCIA = "-";

    private static final String PREFIJO_PACIENTE_INGRESADO = "SIGUE";

    private static final Pattern ANOTACION_CORTA_ENTRE_PARENTESIS = Pattern.compile("^\\([^()]{0,30}\\)$");

    private static final Set<CampoDestino> CAMPOS_TEXTO_LIBRE = Set.of(
            CampoDestino.SEGUIMIENTO_COMENTARIOS,
            CampoDestino.PROFILAXIS_MOTIVO_INADECUACION,
            CampoDestino.MICRO_OTRA_MUESTRA,
            CampoDestino.MICRO_OTRA_RESISTENCIA,
            CampoDestino.PREVENTIVA_OTRA_TECNICA_ELIMINACION_VELLO);

    private ClinicalValueNormalizer() {
    }

    public static boolean esValorAusenteClinico(String valor, CampoDestino campo) {
        if (valor == null || valor.isBlank()) {
            return false;
        }

        if (CAMPOS_TEXTO_LIBRE.contains(campo)) {
            return esValorAusenteEnTextoLibre(valor);
        }

        return esValorAusenteGeneral(valor);
    }

    public static boolean esValorAusenteClinico(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }

        return esValorAusenteGeneral(valor);
    }

    public static boolean esPacienteSigueIngresado(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }

        return TextNormalizer.normalize(valor).startsWith(PREFIJO_PACIENTE_INGRESADO);
    }

    private static boolean esValorAusenteEnTextoLibre(String valor) {
        String normalizado = TextNormalizer.normalize(valor);

        return normalizado.equals(GUION_AUSENCIA) || VALORES_AUSENCIA_TEXTO_LIBRE.contains(normalizado);
    }

    private static boolean esValorAusenteGeneral(String valor) {
        String normalizado = TextNormalizer.normalize(valor);

        if (normalizado.equals(GUION_AUSENCIA)) {
            return true;
        }

        for (String frase : VALORES_AUSENCIA_CLINICA) {
            if (normalizado.equals(frase)) {
                return true;
            }

            if (normalizado.startsWith(frase)) {
                String resto = normalizado.substring(frase.length()).trim();

                if (ANOTACION_CORTA_ENTRE_PARENTESIS.matcher(resto).matches()) {
                    return true;
                }
            }
        }

        return false;
    }
}
