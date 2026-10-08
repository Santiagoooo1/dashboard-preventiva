package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DocumentoPdfDto;

/**
 * Genera el PDF de un informe (Fase 6.9R.2).
 *
 * <p>Recibe un id, nunca una URL: la dirección que abre el navegador la compone
 * el servidor a partir de su configuración. Aceptar una URL del cliente
 * convertiría este servicio en un proxy de peticiones arbitrarias (SSRF).
 */
public interface InformePdfService {

    DocumentoPdfDto generar(Long informeId);
}
