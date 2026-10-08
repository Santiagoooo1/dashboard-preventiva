package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;

/** PDF generado en memoria, listo para entregar (Fase 6.9R.2). */
@Getter
@Builder
public class DocumentoPdfDto {

    private final byte[] contenido;

    /** Nombre ya saneado; no puede contener separadores de ruta. */
    private final String nombreArchivo;
}
