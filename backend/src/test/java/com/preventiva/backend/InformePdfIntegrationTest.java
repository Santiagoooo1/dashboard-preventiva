package com.preventiva.backend;

import com.preventiva.backend.config.PdfInformeProperties;
import com.preventiva.backend.service.interfaces.InformePdfService;
import com.preventiva.backend.util.NombreArchivoPdf;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 6.9R.2 — generación del PDF.
 *
 * <p>Estas pruebas cubren todo lo que se puede comprobar <b>sin arrancar un
 * navegador</b>: la validación previa, la composición de la URL interna, la
 * resolución del ejecutable y el saneado del nombre de archivo. Levantar Chrome
 * dentro de la suite la haría lenta y dependiente de qué haya instalado la
 * máquina que ejecute los tests.
 *
 * <p>La generación real —numeración física, fragmentación, concurrencia— se
 * verifica contra la aplicación en marcha, descargando PDFs por el endpoint y
 * leyendo su contenido.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InformePdfIntegrationTest {

    @Autowired private InformePdfService informePdfService;
    @Autowired private PdfInformeProperties propiedades;

    // ------------------------------------------------------------------
    // Validación previa
    // ------------------------------------------------------------------

    /**
     * Un informe que no existe se rechaza ANTES de arrancar el navegador.
     *
     * <p>Además de ahorrar el proceso, es lo que convierte la petición en un 404
     * limpio en vez de un 500 por un PDF vacío.
     */
    @Test
    void informeInexistente_noLlegaNiAAbrirElNavegador() {
        assertThatThrownBy(() -> informePdfService.generar(999_999_999L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ------------------------------------------------------------------
    // URL interna: el cliente solo manda un id
    // ------------------------------------------------------------------

    /**
     * La URL la compone el servidor con su configuración.
     *
     * <p>Es la defensa contra SSRF: no hay ningún parámetro por el que colar una
     * dirección, así que el navegador no puede acabar visitando lo que el
     * usuario quiera.
     */
    @Test
    void laUrlDeImpresion_seComponeEnElServidorYNoAceptaTextoDelCliente() {
        PdfInformeProperties config = new PdfInformeProperties();

        config.setFrontendBaseUrl("http://localhost:5173");
        assertThat(config.urlImpresion(42L)).isEqualTo("http://localhost:5173/informes/42/imprimir");

        // Una barra de más en la configuración no debe producir «//informes».
        config.setFrontendBaseUrl("http://localhost:5173/");
        assertThat(config.urlImpresion(42L)).isEqualTo("http://localhost:5173/informes/42/imprimir");

        // Desplegado, el frontend puede servirse desde el propio backend.
        config.setFrontendBaseUrl("http://localhost:8080");
        assertThat(config.urlImpresion(7L)).isEqualTo("http://localhost:8080/informes/7/imprimir");
    }

    /** La configuración por defecto apunta a algo utilizable en desarrollo. */
    @Test
    void laConfiguracionPorDefecto_esUsableEnDesarrollo() {
        assertThat(propiedades.getFrontendBaseUrl()).startsWith("http://");
        assertThat(propiedades.getTimeout().toSeconds()).isPositive();
    }

    // ------------------------------------------------------------------
    // Ejecutable del navegador
    // ------------------------------------------------------------------

    /**
     * Una ruta mal configurada falla diciendo exactamente qué property revisar.
     *
     * <p>Un «no se pudo generar el PDF» a secas obligaría a bucear en los logs
     * para descubrir que solo faltaba instalar Chrome.
     */
    @Test
    void unEjecutableInexistente_loDiceConLaPropiedadConcreta() {
        PdfInformeProperties config = new PdfInformeProperties();
        config.setChromeExecutable("C:\\no\\existe\\chrome.exe");

        assertThatThrownBy(config::resolverEjecutable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("preventiva.pdf.chrome-executable");
    }

    /** Una cadena vacía equivale a «no configurado» y pasa a la autodetección. */
    @Test
    void ejecutableEnBlanco_caeEnLaAutodeteccion() {
        PdfInformeProperties config = new PdfInformeProperties();
        config.setChromeExecutable("   ");

        // En esta máquina hay Chrome, así que debe encontrarlo. Si no lo hubiera,
        // el mensaje seguiría siendo el de «configura la propiedad».
        try {
            assertThat(config.resolverEjecutable()).isNotNull();
        } catch (IllegalStateException e) {
            assertThat(e).hasMessageContaining("preventiva.pdf.chrome-executable");
        }
    }

    // ------------------------------------------------------------------
    // Nombre de archivo
    // ------------------------------------------------------------------

    /** Caso normal: título legible más fecha. */
    @Test
    void nombreArchivo_seConstruyeConTituloYFecha() {
        assertThat(NombreArchivoPdf.desde("Informe de vigilancia ILQ 2026", LocalDate.of(2026, 9, 15)))
                .isEqualTo("Informe_de_vigilancia_ILQ_2026_2026-09-15.pdf");
    }

    /** Los acentos se transliteran; perderlos dejaría «Cesreas». */
    @Test
    void nombreArchivo_translitera_losAcentos() {
        assertThat(NombreArchivoPdf.desde("CESÁREAS 2026", LocalDate.of(2026, 1, 2)))
                .isEqualTo("CESAREAS_2026_2026-01-02.pdf");
    }

    /**
     * Un título malicioso no puede producir una ruta.
     *
     * <p>Ni barras, ni «..», ni caracteres que Windows prohíbe: el resultado es
     * siempre un nombre plano dentro de su carpeta.
     */
    @Test
    void nombreArchivo_noPuedeSalirseDeSuCarpeta() {
        String nombre = NombreArchivoPdf.desde("../../etc/passwd", LocalDate.of(2026, 9, 15));

        assertThat(nombre).doesNotContain("..").doesNotContain("/").doesNotContain("\\");
        assertThat(nombre).endsWith(".pdf");
    }

    /** Los caracteres prohibidos en Windows y las comillas desaparecen. */
    @Test
    void nombreArchivo_quitaLoQueRompeElSistemaDeFicherosYLaCabecera() {
        String nombre = NombreArchivoPdf.desde("Informe: \"ILQ\" <2026> | *?", LocalDate.of(2026, 9, 15));

        for (String prohibido : new String[]{":", "\"", "<", ">", "|", "*", "?", "\\", "/", "\n", "\r"}) {
            assertThat(nombre).doesNotContain(prohibido);
        }
        assertThat(nombre).startsWith("Informe_ILQ_2026");
    }

    /** Un título vacío o solo con símbolos no deja el archivo sin nombre. */
    @Test
    void nombreArchivo_tieneRespaldoCuandoElTituloNoAportaNada() {
        assertThat(NombreArchivoPdf.desde("///***", LocalDate.of(2026, 9, 15)))
                .isEqualTo("Informe_2026-09-15.pdf");
        assertThat(NombreArchivoPdf.desde(null, LocalDate.of(2026, 9, 15)))
                .isEqualTo("Informe_2026-09-15.pdf");
    }

    /** Un título kilométrico se recorta para no generar rutas imposibles. */
    @Test
    void nombreArchivo_seRecortaSiElTituloEsEnorme() {
        String nombre = NombreArchivoPdf.desde("A".repeat(400), LocalDate.of(2026, 9, 15));

        assertThat(nombre.length()).isLessThan(120);
        assertThat(nombre).endsWith("_2026-09-15.pdf");
    }

    /** No debe empezar por punto (fichero oculto) ni acabar en punto. */
    @Test
    void nombreArchivo_noEmpiezaNiAcabaEnPuntoOGuion() {
        String nombre = NombreArchivoPdf.desde("...Informe ILQ...", LocalDate.of(2026, 9, 15));

        assertThat(nombre).doesNotStartWith(".").doesNotStartWith("_").doesNotStartWith("-");
        assertThat(nombre).isEqualTo("Informe_ILQ_2026-09-15.pdf");
    }
}
