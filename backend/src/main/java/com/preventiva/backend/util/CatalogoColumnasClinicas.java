package com.preventiva.backend.util;

import com.preventiva.backend.enums.TipoDatoExcel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Columnas clínicas que la aplicación reconoce por su nombre y para las que el
 * código y el tipo están decididos de antemano (Fase 6.9L.1).
 *
 * <p>Existe por dos fallos reales que salieron a la vez al importar un Excel de
 * vigilancia de trauma:
 *
 * <ol>
 *   <li>«LOCALIZACIÓN DE LA INFECCIÓN» se importó como BOOLEANO. La heurística
 *       del asistente ve la palabra «infección» y deduce Sí/No, que acierta con
 *       «INFECCIÓN DE LOCALIZACIÓN QUIRÚRGICA» y falla con esta, que es
 *       categórica (ÓRGANO/ESPACIO, PROFUNDA…). Las filas con localización
 *       rellena —justo las de los pacientes infectados— quedaron fuera de la
 *       importación por no ser un booleano válido.</li>
 *   <li>El código lo generaba el asistente a partir del texto de la cabecera
 *       ({@code infeccionDeLocalizacionQuirurgica}), mientras que los
 *       indicadores clínicos buscan el código canónico
 *       ({@code infeccionLocalizacionQuirurgica}). Nunca coincidían, así que el
 *       bloque de ILQ del dashboard salía vacío sin dar ningún error.</li>
 * </ol>
 *
 * <p>Un nombre reconocido aquí manda sobre cualquier heurística: si sabemos qué
 * es la columna, no hay nada que adivinar. Lo que no esté en esta lista sigue
 * pasando por las reglas genéricas de siempre.
 *
 * <p>El emparejamiento va por {@link TextNormalizer#normalize} —sin acentos,
 * en mayúsculas y con los espacios colapsados—, porque estos ficheros vienen de
 * exportaciones hospitalarias donde la misma columna aparece con y sin tilde.
 */
public final class CatalogoColumnasClinicas {

    /** Una columna reconocida: qué campo es y de qué tipo, sin lugar a dudas. */
    public record ColumnaCanonica(String codigo, TipoDatoExcel tipoDato, boolean esComun) {
    }

    /**
     * Cabeceras reconocidas y su significado.
     *
     * <p>Cada entrada lista las variantes vistas en ficheros reales. No se
     * intenta cubrir todas las redacciones posibles: una columna que no se
     * reconozca cae en las reglas genéricas, que es un resultado aceptable. Lo
     * que no es aceptable es reconocerla mal.
     */
    private static final Map<String, ColumnaCanonica> POR_NOMBRE = construir();

    private CatalogoColumnasClinicas() {
    }

    private static Map<String, ColumnaCanonica> construir() {
        Map<String, ColumnaCanonica> mapa = new LinkedHashMap<>();

        // Resultado principal de la vigilancia: Sí/No.
        registrar(mapa, new ColumnaCanonica("infeccionLocalizacionQuirurgica", TipoDatoExcel.BOOLEANO, false),
                "INFECCION DE LOCALIZACION QUIRURGICA",
                "INFECCION DE LOCALIZACION QUIRURGICA (ILQ)",
                "INFECCION LOCALIZACION QUIRURGICA",
                "ILQ",
                "INFECCION QUIRURGICA");

        // Dónde se localizó la infección: categórica, NO Sí/No. Es la columna
        // que motivó esta corrección.
        registrar(mapa, new ColumnaCanonica("localizacionInfeccion", TipoDatoExcel.TEXTO, false),
                "LOCALIZACION DE LA INFECCION",
                "LOCALIZACION INFECCION",
                "LOCALIZACION DE ILQ",
                "TIPO DE ILQ",
                "TIPO DE INFECCION");

        // Cuándo se detectó: fecha, tampoco Sí/No.
        //
        // El código es `fechaInfeccion`, que es el que ya usa el modelo genérico
        // (ver scripts/demo/seed-demo-ilq.sql) junto a los otros cuatro campos de
        // este catálogo. Un segundo nombre para el mismo concepto clínico
        // obligaría a consultar los dos en cada métrica, y tarde o temprano
        // alguno se quedaría sin mirar.
        registrar(mapa, new ColumnaCanonica("fechaInfeccion", TipoDatoExcel.FECHA, false),
                "FECHA DE INFECCION DE LOCALIZACION QUIRURGICA",
                "FECHA DE INFECCION",
                "FECHA INFECCION",
                "FECHA DE ILQ",
                "FECHA ILQ");

        registrar(mapa, new ColumnaCanonica("adecuacionProfilaxis", TipoDatoExcel.TEXTO, false),
                "ADECUACION DE PROFILAXIS",
                "ADECUACION PROFILAXIS",
                "PROFILAXIS ADECUADA",
                "ADECUACION DE LA PROFILAXIS");

        registrar(mapa, new ColumnaCanonica("motivoInadecuacionProfilaxis", TipoDatoExcel.TEXTO, false),
                "MOTIVOS DE INADECUACION",
                "MOTIVO DE INADECUACION",
                "MOTIVOS INADECUACION",
                "MOTIVO INADECUACION",
                "MOTIVOS DE INADECUACION DE PROFILAXIS");

        return Map.copyOf(mapa);
    }

    private static void registrar(
            Map<String, ColumnaCanonica> mapa, ColumnaCanonica canonica, String... nombres) {
        for (String nombre : nombres) {
            mapa.put(TextNormalizer.normalize(nombre), canonica);
        }
    }

    /**
     * Qué campo clínico es esta cabecera, si la reconocemos.
     *
     * @param nombreColumna cabecera tal cual viene del archivo
     * @return el campo canónico, o vacío si no está en el catálogo
     */
    public static Optional<ColumnaCanonica> resolver(String nombreColumna) {
        if (nombreColumna == null || nombreColumna.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(POR_NOMBRE.get(TextNormalizer.normalize(nombreColumna)));
    }

    /** Códigos canónicos que el catálogo puede producir, sin repeticiones. */
    public static List<String> codigosCanonicos() {
        return POR_NOMBRE.values().stream().map(ColumnaCanonica::codigo).distinct().toList();
    }
}
