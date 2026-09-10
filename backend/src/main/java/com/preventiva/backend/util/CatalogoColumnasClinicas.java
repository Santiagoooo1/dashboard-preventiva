package com.preventiva.backend.util;

import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Columnas clínicas que la aplicación reconoce por su nombre y para las que el
 * código y el tipo están decididos de antemano (Fases 6.9L.1 y 6.9M).
 *
 * <p>Existe por un fallo con consecuencias: «LOCALIZACIÓN DE LA INFECCIÓN» se
 * importó como BOOLEANO porque su nombre contiene la palabra «infección». Las
 * únicas filas que traen localización son las de los pacientes infectados, así
 * que fallaron la validación y se perdieron justo los casos que se vigilan.
 *
 * <p>La regla que evita que eso vuelva a pasar es de precedencia: <b>si la
 * columna se reconoce aquí, no hay nada que adivinar</b>. El tipo lo fija el
 * significado de la columna, nunca sus valores ni una heurística sobre el
 * nombre. «Adecuación de profilaxis» es TEXTO aunque hoy traiga SI/NO, porque
 * mañana traerá ADECUADA/INADECUADA/NO_APLICA.
 *
 * <p>El reconocimiento es por <b>nombre completo normalizado</b>, no por
 * subcadenas. Es deliberado: «LOCALIZACIÓN DEL HOSPITAL» no es la localización
 * de una infección, y «CULTIVO DE ILQ» no es el campo ILQ. Se prefiere no
 * reconocer una columna —que cae en las reglas genéricas de siempre, un
 * resultado aceptable— a reconocerla mal.
 */
public final class CatalogoColumnasClinicas {

    /** De dónde sale el reconocimiento, para poder auditar por qué se decidió. */
    public enum OrigenReconocimiento {
        /** El nombre coincide con la denominación principal del concepto. */
        CANONICA_EXACTA,
        /** El nombre es una de las variantes conocidas del mismo concepto. */
        ALIAS
    }

    /** Una columna reconocida: qué campo es, de qué tipo, y por qué lo sabemos. */
    public record ColumnaCanonica(
            String codigo,
            TipoDatoExcel tipoDato,
            boolean esComun,
            /** Nombre recomendado para mostrar, cuando el del archivo es críptico. */
            String etiquetaRecomendada,
            OrigenReconocimiento origen) {
    }

    /** Un concepto clínico y todos los nombres con los que aparece en los ficheros. */
    private record Concepto(
            String codigo,
            TipoDatoExcel tipoDato,
            boolean esComun,
            String etiquetaRecomendada,
            List<String> nombres) {
    }

    private static final Map<String, ColumnaCanonica> POR_NOMBRE = construir();

    private CatalogoColumnasClinicas() {
    }

    /**
     * Conceptos reconocidos. El primer nombre de cada lista es la denominación
     * principal; el resto son variantes vistas en ficheros reales de distintos
     * hospitales.
     *
     * <p>No se intenta cubrir toda redacción posible. Cada entrada se añade
     * porque alguien la ha escrito así, no por si acaso.
     */
    private static List<Concepto> conceptos() {
        return List.of(

                // Resultado principal de la vigilancia. Sí/No de verdad.
                //
                // «ILQ» a secas se acepta solo como cabecera completa: hacer
                // contains("ILQ") capturaría «CULTIVO DE ILQ» y «REINGRESO por
                // ILQ», que son otras cosas.
                new Concepto("infeccionLocalizacionQuirurgica", TipoDatoExcel.BOOLEANO, false,
                        "Infección de localización quirúrgica",
                        List.of("INFECCION DE LOCALIZACION QUIRURGICA",
                                "INFECCION DE LOCALIZACION QUIRURGICA (ILQ)",
                                "INFECCION LOCALIZACION QUIRURGICA",
                                "INFECCION QUIRURGICA",
                                "ILQ")),

                // Dónde se localizó: categórica (ÓRGANO/ESPACIO, PROFUNDA…),
                // nunca Sí/No. Es la columna que motivó todo esto.
                new Concepto("localizacionInfeccion", TipoDatoExcel.TEXTO, false,
                        "Localización de la infección",
                        List.of("LOCALIZACION DE LA INFECCION",
                                "LOCALIZACION INFECCION",
                                "LOCALIZACION ILQ",
                                "LOCALIZACION DE ILQ",
                                "LOCALIZACION DE LA ILQ",
                                "SITIO DE LA INFECCION",
                                "SITIO DE INFECCION",
                                "TIPO DE ILQ")),

                // Cuándo se detectó. El código es el que ya usa el modelo
                // genérico (ver scripts/demo/seed-demo-ilq.sql).
                new Concepto("fechaInfeccion", TipoDatoExcel.FECHA, false,
                        "Fecha de la infección",
                        List.of("FECHA DE INFECCION DE LOCALIZACION QUIRURGICA",
                                "FECHA DE INFECCION",
                                "FECHA INFECCION",
                                "FECHA DE ILQ",
                                "FECHA ILQ",
                                "FECHA DE DIAGNOSTICO DE ILQ")),

                // Resultado de la adecuación. TEXTO aunque hoy traiga SI/NO:
                // el vocabulario real varía entre hospitales y con el tiempo.
                new Concepto("adecuacionProfilaxis", TipoDatoExcel.TEXTO, false,
                        "Adecuación de profilaxis",
                        List.of("ADECUACION DE PROFILAXIS",
                                "ADECUACION PROFILAXIS",
                                "ADECUACION DE LA PROFILAXIS",
                                "ADECUACION DE LA PROFILAXIS ANTIBIOTICA",
                                "ADECUACION DE PROFILAXIS ANTIBIOTICA",
                                "PROFILAXIS ADECUADA")),

                new Concepto("motivoInadecuacionProfilaxis", TipoDatoExcel.TEXTO, false,
                        "Motivo de inadecuación",
                        List.of("MOTIVOS DE INADECUACION",
                                "MOTIVO DE INADECUACION",
                                "MOTIVOS INADECUACION",
                                "MOTIVO INADECUACION",
                                "MOTIVOS DE INADECUACION DE PROFILAXIS",
                                "CAUSA DE INADECUACION",
                                "CAUSAS DE INADECUACION",
                                "MOTIVO PROFILAXIS INADECUADA")),

                // Fecha del evento principal del dataset.
                //
                // Solo denominaciones que dicen de qué es la fecha. Una columna
                // llamada «FECHA» a secas es demasiado ambigua: podría ser el
                // ingreso, el alta o el nacimiento, y equivocarse aquí desplaza
                // todo el eje temporal del dashboard.
                new Concepto("fechaEvento", TipoDatoExcel.FECHA, true,
                        "Fecha de la intervención",
                        List.of("FECHA CIRUGIA",
                                "FECHA DE CIRUGIA",
                                "FECHA DE LA CIRUGIA",
                                "FECHA INTERVENCION",
                                "FECHA DE INTERVENCION",
                                "FECHA DE LA INTERVENCION",
                                "FECHA PROCEDIMIENTO",
                                "FECHA DE PROCEDIMIENTO",
                                "FECHA DEL PROCEDIMIENTO")));
    }

    private static Map<String, ColumnaCanonica> construir() {
        Map<String, ColumnaCanonica> mapa = new LinkedHashMap<>();
        for (Concepto concepto : conceptos()) {
            List<String> nombres = concepto.nombres();
            for (int i = 0; i < nombres.size(); i++) {
                OrigenReconocimiento origen = i == 0
                        ? OrigenReconocimiento.CANONICA_EXACTA
                        : OrigenReconocimiento.ALIAS;
                mapa.put(normalizar(nombres.get(i)), new ColumnaCanonica(
                        concepto.codigo(), concepto.tipoDato(), concepto.esComun(),
                        concepto.etiquetaRecomendada(), origen));
            }
        }
        return Map.copyOf(mapa);
    }

    /**
     * Normalización usada SOLO para reconocer. El nombre que ve el usuario no
     * se toca nunca.
     *
     * <p>Sobre {@link TextNormalizer#normalize} —sin acentos, en mayúsculas,
     * espacios colapsados— añade el tratamiento de los separadores con los que
     * un mismo encabezado se escribe de forma distinta en cada exportación:
     * {@code LOCALIZACION_INFECCION}, {@code Localización-Infección} y
     * {@code LOCALIZACIÓN (INFECCIÓN)} son el mismo campo.
     *
     * <p>Los paréntesis se convierten en espacio, no se eliminan con su
     * contenido: en «INFECCION DE LOCALIZACION QUIRURGICA (ILQ)» lo de dentro es
     * una aclaración, y quitarla o conservarla lleva al mismo sitio porque ambas
     * formas están registradas.
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String base = TextNormalizer.normalize(texto);
        return base
                .replaceAll("[_\\-.:;/()\\[\\]{}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Qué campo clínico es esta cabecera, si la reconocemos.
     *
     * <p>Coincidencia por nombre completo normalizado: nunca por subcadena. Una
     * columna que no esté registrada devuelve vacío y sigue por las reglas
     * genéricas, que es el comportamiento correcto ante la duda.
     *
     * @param nombreColumna cabecera tal cual viene del archivo
     */
    public static Optional<ColumnaCanonica> resolver(String nombreColumna) {
        if (nombreColumna == null || nombreColumna.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(POR_NOMBRE.get(normalizar(nombreColumna)));
    }

    /** Códigos canónicos que el catálogo puede producir, sin repeticiones. */
    public static List<String> codigosCanonicos() {
        return POR_NOMBRE.values().stream().map(ColumnaCanonica::codigo).distinct().toList();
    }

    /** Todos los nombres reconocidos de un código, para diagnóstico y tests. */
    public static List<String> nombresDe(String codigo) {
        List<String> nombres = new ArrayList<>();
        POR_NOMBRE.forEach((nombre, canonica) -> {
            if (canonica.codigo().equals(codigo)) {
                nombres.add(nombre);
            }
        });
        return List.copyOf(nombres);
    }
}
