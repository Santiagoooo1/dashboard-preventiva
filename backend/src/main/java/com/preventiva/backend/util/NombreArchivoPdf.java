package com.preventiva.backend.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Nombre de descarga del PDF de un informe (Fase 6.9R.2).
 *
 * <p>Se construye desde el título visible y la fecha, por ejemplo
 * {@code Informe_de_vigilancia_ILQ_2026_2026-09-15.pdf}.
 *
 * <p>Vive aparte del servicio para poder probarlo sin arrancar un navegador:
 * es donde se decide qué caracteres llegan a un sistema de ficheros y a una
 * cabecera HTTP, y eso merece pruebas propias.
 */
public final class NombreArchivoPdf {

    private static final int MAX_LONGITUD = 80;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String POR_DEFECTO = "Informe";

    private NombreArchivoPdf() {
    }

    public static String desde(String tituloVisible, LocalDate fecha) {
        String base = sanear(tituloVisible);
        if (base.isEmpty()) {
            base = POR_DEFECTO;
        }
        return base + "_" + fecha.format(FECHA) + ".pdf";
    }

    /**
     * Deja solo lo que es seguro en un nombre de archivo.
     *
     * <p>Los acentos se transliteran en vez de descartarse —«Cesáreas» debe
     * seguir leyéndose «Cesareas», no «Cesreas»—, y desaparece todo lo que un
     * sistema de ficheros o una cabecera HTTP puedan interpretar: separadores
     * de ruta, {@code ..}, comillas y caracteres de control. Un título como
     * {@code ../../passwd} no puede producir una ruta que salga de su carpeta.
     */
    private static String sanear(String texto) {
        if (texto == null) {
            return "";
        }
        String plano = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        String limpio = plano
                // Prohibidos en Windows, separadores de ruta y controles.
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                // Cualquier secuencia de puntos: mata «..» y también «....».
                .replaceAll("\\.{2,}", " ")
                // Lista blanca: el resto pasa a espacio.
                .replaceAll("[^A-Za-z0-9 _.-]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .replace(' ', '_');

        if (limpio.length() > MAX_LONGITUD) {
            limpio = limpio.substring(0, MAX_LONGITUD);
        }
        // Ni puntos ni guiones en los extremos: uno inicial esconde el fichero
        // en sistemas Unix y uno final lo rechaza Windows.
        return limpio.replaceAll("^[._-]+", "").replaceAll("[._-]+$", "");
    }
}
