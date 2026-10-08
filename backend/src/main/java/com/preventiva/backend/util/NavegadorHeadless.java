package com.preventiva.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Imprime una URL a PDF con el Chrome instalado (Fase 6.9R.2).
 *
 * <p><b>Por qué DevTools y no {@code --print-to-pdf} a secas.</b> Se probó
 * primero la vía simple, que es la preferida. Funciona y respeta las margin
 * boxes, pero no sabe esperar: con un presupuesto de tiempo virtual corto,
 * Chrome imprimió el informe a medias —con el texto «Calculando…» dentro— y
 * devolvió código de salida 0. Un PDF clínico incompleto que se entrega como si
 * estuviera bien es el peor resultado posible, así que hace falta preguntarle al
 * documento si está listo, y eso solo se puede hacer hablando con el navegador.
 *
 * <p>El protocolo se habla con {@link java.net.http.WebSocket}, que viene en el
 * JDK: no se ha añadido Playwright, Puppeteer ni ninguna otra dependencia.
 *
 * <p>Ventaja añadida: {@code Page.printToPDF} devuelve los bytes por el propio
 * socket, así que no hay ningún fichero temporal de PDF que nombrar, vigilar ni
 * borrar.
 */
public class NavegadorHeadless implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NavegadorHeadless.class);

    /** Cada cuánto se le vuelve a preguntar a la página si ya está lista. */
    private static final Duration INTERVALO_SONDEO = Duration.ofMillis(150);

    /** Pasadas de borrado del perfil temporal, con espera creciente entre ellas. */
    private static final int INTENTOS_BORRADO = 8;

    /** Base de la espera entre pasadas; se multiplica por el número de intento. */
    private static final Duration ESPERA_ENTRE_BORRADOS = Duration.ofMillis(500);

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger secuencia = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonNode>> pendientes = new ConcurrentHashMap<>();

    private final Process proceso;
    private final Path perfilTemporal;
    private final WebSocket socket;
    private final StringBuilder acumulador = new StringBuilder();

    /**
     * Arranca el navegador y abre el canal de control.
     *
     * <p>Recibe un <b>instante límite</b>, no una duración. Antes cada fase
     * —arranque, navegación, espera, impresión— estrenaba su propio presupuesto,
     * así que un timeout configurado de 1 s permitía tardar casi 4 s y acababa
     * devolviendo el PDF como si nada. El plazo es uno solo para todo.
     *
     * @param ejecutable binario ya resuelto por configuración; nunca texto del cliente.
     */
    public NavegadorHeadless(Path ejecutable, Instant limite) throws IOException, InterruptedException {
        this.perfilTemporal = Files.createTempDirectory("preventiva-chrome-");

        // Argumentos como lista: no hay shell por medio, así que no hay nada que
        // pueda interpretar comillas ni encadenar comandos.
        List<String> argumentos = List.of(
                ejecutable.toString(),
                "--headless",
                "--disable-gpu",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-extensions",
                // Sin red externa: la página de impresión solo debe hablar con
                // el backend local. Ver la auditoría de recursos de terceros.
                "--disable-background-networking",
                "--disable-sync",
                // Puerto 0 = que lo elija el sistema. Dos generaciones
                // simultáneas no pueden chocar por el puerto de depuración.
                "--remote-debugging-port=0",
                "--user-data-dir=" + perfilTemporal,
                "about:blank");

        ProcessBuilder constructor = new ProcessBuilder(argumentos);
        constructor.redirectErrorStream(true);
        this.proceso = constructor.start();

        try {
            int puerto = esperarPuertoDepuracion(limite);
            String urlSocket = localizarPagina(puerto, limite);
            this.socket = conectar(urlSocket, limite);
        } catch (RuntimeException | IOException | InterruptedException e) {
            cerrarNavegador();
            throw e;
        }
    }

    /**
     * Carga la URL, espera a que el documento se declare listo e imprime.
     *
     * @param selectorListo selector CSS que el documento solo expone cuando ha
     *                      terminado de calcularse y pintarse.
     */
    public byte[] imprimirPdf(String url, String selectorListo, Instant limite)
            throws IOException, InterruptedException {
        enviar("Page.enable", json.createObjectNode(), limite);
        ObjectNode navegar = json.createObjectNode();
        navegar.put("url", url);
        JsonNode respuesta = enviar("Page.navigate", navegar, limite);
        if (respuesta.hasNonNull("errorText")) {
            throw new IOException("El navegador no pudo abrir la página del informe: "
                    + respuesta.get("errorText").asText());
        }

        esperarDocumentoListo(selectorListo, limite);

        ObjectNode opciones = json.createObjectNode();
        // Los fondos forman parte de la información en las gráficas.
        opciones.put("printBackground", true);
        // Manda el @page del documento: tamaño A4, márgenes y margin boxes con
        // la cabecera, el pie y la numeración física.
        opciones.put("preferCSSPageSize", true);
        JsonNode resultado = enviar("Page.printToPDF", opciones, limite);
        if (!resultado.hasNonNull("data")) {
            throw new IOException("El navegador no devolvió ningún PDF.");
        }
        return Base64.getDecoder().decode(resultado.get("data").asText());
    }

    /**
     * Sondea el DOM hasta que aparece el selector de «listo».
     *
     * <p>Esto es lo que garantiza que no se imprime antes de que la respuesta de
     * la API, las tablas y los SVG estén en la página. No hay ninguna espera
     * fija: si el informe tarda más, se sigue esperando hasta el timeout.
     */
    private void esperarDocumentoListo(String selectorListo, Instant limite)
            throws IOException, InterruptedException {
        String expresion = "!!document.querySelector(" + comillasJs(selectorListo) + ")";
        while (Instant.now().isBefore(limite)) {
            ObjectNode evaluar = json.createObjectNode();
            evaluar.put("expression", expresion);
            evaluar.put("returnByValue", true);
            JsonNode resultado = enviar("Runtime.evaluate", evaluar, limite);
            if (resultado.path("result").path("value").asBoolean(false)) {
                return;
            }
            Thread.sleep(INTERVALO_SONDEO.toMillis());
        }
        throw new IOException("El informe no terminó de renderizarse dentro del tiempo permitido.");
    }

    // ------------------------------------------------------------------
    // Arranque y conexión
    // ------------------------------------------------------------------

    /**
     * Chrome escribe el puerto que le ha tocado en un fichero del perfil. Es la
     * forma soportada de descubrirlo cuando se pide el puerto 0.
     */
    private int esperarPuertoDepuracion(Instant limite) throws IOException, InterruptedException {
        Path fichero = perfilTemporal.resolve("DevToolsActivePort");
        while (Instant.now().isBefore(limite)) {
            if (!proceso.isAlive()) {
                throw new IOException("El navegador terminó inesperadamente: " + salidaBreve());
            }
            if (Files.exists(fichero)) {
                List<String> lineas = Files.readAllLines(fichero);
                if (!lineas.isEmpty() && !lineas.get(0).isBlank()) {
                    return Integer.parseInt(lineas.get(0).trim());
                }
            }
            Thread.sleep(50);
        }
        throw new IOException("El navegador no abrió su puerto de control a tiempo.");
    }

    /** Pide a Chrome la pestaña sobre la que trabajar. */
    private String localizarPagina(int puerto, Instant limite) throws IOException, InterruptedException {
        Duration restante = restante(limite);
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(restante).build();
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto + "/json/list"))
                .timeout(restante)
                .GET()
                .build();
        HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
        for (JsonNode destino : json.readTree(respuesta.body())) {
            if ("page".equals(destino.path("type").asText())) {
                return destino.path("webSocketDebuggerUrl").asText();
            }
        }
        throw new IOException("El navegador no expuso ninguna pestaña utilizable.");
    }

    private WebSocket conectar(String urlSocket, Instant limite) throws IOException, InterruptedException {
        Duration restante = restante(limite);
        try {
            return HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .connectTimeout(restante)
                    .buildAsync(URI.create(urlSocket), new Escucha())
                    .get(restante.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IOException("No se pudo abrir el canal de control del navegador.", e);
        }
    }

    // ------------------------------------------------------------------
    // Mensajería
    // ------------------------------------------------------------------

    private JsonNode enviar(String metodo, ObjectNode parametros, Instant limite)
            throws IOException, InterruptedException {
        int id = secuencia.incrementAndGet();
        ObjectNode mensaje = json.createObjectNode();
        mensaje.put("id", id);
        mensaje.put("method", metodo);
        mensaje.set("params", parametros);

        CompletableFuture<JsonNode> respuesta = new CompletableFuture<>();
        pendientes.put(id, respuesta);
        socket.sendText(json.writeValueAsString(mensaje), true);

        try {
            return respuesta.get(restante(limite).toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            pendientes.remove(id);
            throw new IOException("El navegador no respondió a " + metodo + " a tiempo.", e);
        }
    }

    /** Lo que queda del plazo común, nunca cero: un timeout de 0 no expira. */
    private static Duration restante(Instant limite) {
        Duration queda = Duration.between(Instant.now(), limite);
        return queda.isNegative() || queda.isZero() ? Duration.ofMillis(1) : queda;
    }

    /** Escapa una cadena para incrustarla en una expresión JavaScript. */
    private static String comillasJs(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private final class Escucha implements WebSocket.Listener {
        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence datos, boolean ultimo) {
            acumulador.append(datos);
            if (ultimo) {
                String completo = acumulador.toString();
                acumulador.setLength(0);
                try {
                    JsonNode nodo = json.readTree(completo);
                    if (nodo.hasNonNull("id")) {
                        CompletableFuture<JsonNode> futuro = pendientes.remove(nodo.get("id").asInt());
                        if (futuro != null) {
                            if (nodo.hasNonNull("error")) {
                                futuro.completeExceptionally(new IOException(
                                        "El navegador rechazó la orden: " + nodo.get("error").path("message").asText()));
                            } else {
                                futuro.complete(nodo.path("result"));
                            }
                        }
                    }
                    // Los eventos sin «id» son notificaciones del navegador y no
                    // interesan aquí: el flujo es petición/respuesta.
                } catch (IOException e) {
                    log.warn("Mensaje ilegible del navegador: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Cierre
    // ------------------------------------------------------------------

    @Override
    public void close() {
        cerrarNavegador();
    }

    private void cerrarNavegador() {
        try {
            if (socket != null) {
                socket.abort();
            }
        } catch (RuntimeException e) {
            log.debug("El canal de control ya estaba cerrado.");
        }
        if (proceso != null) {
            // destroy() pide salir; si no obedece se fuerza. Lo que no puede
            // pasar es dejar procesos de navegador vivos por cada PDF.
            proceso.destroy();
            try {
                if (!proceso.waitFor(5, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                    proceso.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                proceso.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        borrarPerfil();
    }

    /**
     * Borra el perfil temporal.
     *
     * <p>Se intenta una vez aquí y, si Windows aún tiene los ficheros
     * bloqueados, el resto se hace en un hilo aparte. Chrome sigue volcando sus
     * bases LevelDB —LOCK, LOG, MANIFEST— unos segundos después de morir, y
     * esperar a que suelte todo dentro de la petición retrasaría la descarga
     * por algo que al usuario no le importa. Se midió: un perfil que resiste
     * varios intentos seguidos se borra sin problema poco después.
     *
     * <p>Lo que sí es síncrono es matar el proceso: un navegador zombi por cada
     * PDF sería un problema de verdad.
     */
    private void borrarPerfil() {
        if (perfilTemporal == null || intentarBorrarArbol()) {
            return;
        }
        Thread barredor = new Thread(this::barrerHastaLograrlo, "pdf-limpieza-perfil");
        // Demonio: no debe impedir que la aplicación se apague.
        barredor.setDaemon(true);
        barredor.start();
    }

    private void barrerHastaLograrlo() {
        for (int intento = 1; intento <= INTENTOS_BORRADO; intento++) {
            try {
                Thread.sleep(ESPERA_ENTRE_BORRADOS.toMillis() * intento);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (intentarBorrarArbol()) {
                return;
            }
        }
        // Se deja constancia para que no pase desapercibido si se repite.
        log.warn("Quedó sin borrar el perfil temporal {}", perfilTemporal);
    }

    /** @return true si el árbol ya no existe tras esta pasada. */
    private boolean intentarBorrarArbol() {
        if (!Files.exists(perfilTemporal)) {
            return true;
        }
        List<Path> rutas;
        try (var flujo = Files.walk(perfilTemporal)) {
            // Se materializa la lista antes de borrar: recorrer y modificar el
            // árbol a la vez hace saltar el walk si Chrome añade un fichero.
            rutas = flujo.sorted(Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            return false;
        }

        for (Path ruta : rutas) {
            try {
                Files.deleteIfExists(ruta);
            } catch (IOException e) {
                // Fichero aún bloqueado por Chrome: se reintentará más tarde.
            }
        }
        return !Files.exists(perfilTemporal);
    }

    /** Primeras líneas de la salida del proceso, para diagnosticar un arranque fallido. */
    private String salidaBreve() {
        try (var flujo = proceso.getInputStream()) {
            byte[] bytes = flujo.readNBytes(500);
            return new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "sin detalles";
        }
    }
}
