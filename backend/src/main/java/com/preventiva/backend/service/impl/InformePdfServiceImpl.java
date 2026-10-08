package com.preventiva.backend.service.impl;

import com.preventiva.backend.config.PdfInformeProperties;
import com.preventiva.backend.dto.DocumentoPdfDto;
import com.preventiva.backend.dto.InformeClinicoResponseDto;
import com.preventiva.backend.service.interfaces.InformeClinicoService;
import com.preventiva.backend.service.interfaces.InformePdfService;
import com.preventiva.backend.util.NavegadorHeadless;
import com.preventiva.backend.util.NombreArchivoPdf;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Convierte un informe en PDF abriendo su propia ruta de impresión (Fase 6.9R.2).
 *
 * <p>No se reconstruye el documento en Java ni se recalcula ninguna métrica: el
 * navegador carga {@code /informes/{id}/imprimir}, que es exactamente la página
 * que el usuario ve al imprimir a mano. Un solo maquetado para las dos salidas,
 * así que no pueden divergir.
 *
 * <p>El PDF no se guarda en ningún sitio: se genera, se entrega y desaparece.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InformePdfServiceImpl implements InformePdfService {

    /**
     * Marca que la página de impresión solo pone cuando ha terminado de
     * calcularse y pintarse. Es el contrato entre las dos mitades.
     */
    private static final String SELECTOR_LISTO = "[data-informe-estado=\"listo\"]";

    private final InformeClinicoService informeClinicoService;
    private final PdfInformeProperties propiedades;

    @Override
    public DocumentoPdfDto generar(Long informeId) {
        // Valida que el informe existe y es accesible ANTES de arrancar un
        // navegador: si no, se paga medio segundo de proceso para acabar en 404.
        InformeClinicoResponseDto informe = informeClinicoService.obtenerEstructura(informeId);

        Path ejecutable = propiedades.resolverEjecutable();
        String url = propiedades.urlImpresion(informeId);
        // Un único plazo para arrancar el navegador, cargar la página, esperar a
        // que esté lista e imprimir. Repartirlo por fases haría que el timeout
        // configurado se multiplicara por el número de fases.
        Instant limite = Instant.now().plus(propiedades.getTimeout());

        long inicio = System.nanoTime();
        log.info("Generando PDF del informe {}", informeId);

        try (NavegadorHeadless navegador = new NavegadorHeadless(ejecutable, limite)) {
            byte[] pdf = navegador.imprimirPdf(url, SELECTOR_LISTO, limite);
            validarPdf(pdf);

            long milis = (System.nanoTime() - inicio) / 1_000_000;
            log.info("PDF del informe {} generado en {} ms ({} bytes)", informeId, milis, pdf.length);

            return DocumentoPdfDto.builder()
                    .contenido(pdf)
                    .nombreArchivo(NombreArchivoPdf.desde(informe.getTituloVisible(), LocalDate.now()))
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Generación del PDF del informe {} interrumpida", informeId);
            throw new IllegalStateException("La generación del PDF se interrumpió.");
        } catch (IOException e) {
            long milis = (System.nanoTime() - inicio) / 1_000_000;
            // El detalle técnico va al log; al usuario le llega una frase útil.
            log.error("Fallo al generar el PDF del informe {} tras {} ms: {}", informeId, milis, e.getMessage());
            throw new IllegalStateException("No se pudo generar el PDF del informe.");
        }
    }

    /**
     * Un PDF que no empieza por {@code %PDF} no es un PDF.
     *
     * <p>Sin esta comprobación, un fallo del navegador podría entregarse como
     * descarga correcta y el usuario se encontraría con un archivo ilegible y un
     * HTTP 200 diciéndole que todo fue bien.
     */
    private void validarPdf(byte[] pdf) throws IOException {
        if (pdf == null || pdf.length < 5
                || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
            throw new IOException("El navegador devolvió un documento que no es un PDF.");
        }
    }
}
