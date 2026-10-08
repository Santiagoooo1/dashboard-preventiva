package com.preventiva.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Configuración de la generación de PDF (Fase 6.9R.2).
 *
 * <p>Nada de esto se acepta del cliente: el usuario solo manda un
 * {@code informeId}. La URL que se abre y el ejecutable que se lanza salen de
 * aquí, que es lo que impide convertir el endpoint en un SSRF o en una vía para
 * ejecutar binarios arbitrarios.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "preventiva.pdf")
public class PdfInformeProperties {

    /**
     * Base desde la que el navegador carga la ruta de impresión.
     *
     * <p>En desarrollo apunta al servidor de Vite. Desplegado, cuando el
     * frontend se sirva desde el propio Spring Boot, será la URL de la
     * aplicación (por ejemplo {@code http://localhost:8080}).
     *
     * <p>Se deja {@code localhost} y no {@code 127.0.0.1}: en esta máquina Vite
     * escucha solo en el loopback IPv6 (::1), así que fijar la IPv4 daría
     * «conexión rechazada». {@code localhost} resuelve a la que el servidor
     * tenga abierta, sea cual sea.
     */
    private String frontendBaseUrl = "http://localhost:5173";

    /**
     * Ruta al ejecutable de Chrome. Si se deja vacía se intenta autodetectar
     * entre un puñado de ubicaciones conocidas.
     *
     * <p>La configuración explícita siempre gana. No se busca «cualquier
     * navegador que haya por el disco»: o está en la lista de sitios estándar o
     * el administrador lo declara.
     */
    private String chromeExecutable;

    /** Tiempo máximo de una generación completa. Al agotarse se mata el proceso. */
    private Duration timeout = Duration.ofSeconds(60);

    /**
     * Presupuesto de tiempo virtual del navegador, en milisegundos.
     *
     * <p>No es un «sleep»: el reloj virtual de Chrome se detiene mientras haya
     * peticiones de red pendientes, así que este presupuesto se consume con
     * trabajo real de la página, no con espera. Aun así el resultado se verifica
     * después: ver {@code InformePdfServiceImpl}.
     */
    private long virtualTimeBudgetMs = 30_000;

    /** Ubicaciones estándar, por sistema. No se explora fuera de esta lista. */
    private static final List<String> CANDIDATOS_WINDOWS = List.of(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");

    /**
     * Candidatos en Linux. La arquitectura los contempla, pero <b>no están
     * probados</b>: la fase 6.9R.2 se ha verificado solo en Windows.
     */
    private static final List<String> CANDIDATOS_LINUX = List.of(
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
            "/snap/bin/chromium");

    /**
     * Resuelve el navegador a usar.
     *
     * @throws IllegalStateException si no hay ninguno, con un mensaje que dice
     *         exactamente qué property configurar. Un «no se pudo generar el
     *         PDF» a secas obligaría a leer logs para descubrir que solo falta
     *         instalar Chrome.
     */
    public Path resolverEjecutable() {
        if (chromeExecutable != null && !chromeExecutable.isBlank()) {
            Path declarado = Path.of(chromeExecutable.trim());
            if (!Files.isExecutable(declarado)) {
                throw new IllegalStateException(
                        "El navegador configurado en preventiva.pdf.chrome-executable no existe o no es "
                                + "ejecutable: " + declarado);
            }
            return declarado;
        }

        for (String candidato : esWindows() ? CANDIDATOS_WINDOWS : CANDIDATOS_LINUX) {
            Path ruta = Path.of(candidato);
            if (Files.isExecutable(ruta)) {
                return ruta;
            }
        }

        throw new IllegalStateException(
                "No se ha encontrado Chrome para generar el PDF. Indica su ruta en la propiedad "
                        + "preventiva.pdf.chrome-executable.");
    }

    /** URL interna de la ruta de impresión. El id es un número, no texto libre. */
    public String urlImpresion(long informeId) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/informes/" + informeId + "/imprimir";
    }

    private static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
