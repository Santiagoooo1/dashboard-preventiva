package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.ComparacionInteranualRequestDto;
import com.preventiva.backend.dto.ComparacionInteranualResponseDto;

public interface ComparacionInteranualService {

    /**
     * Compara un mismo concepto clínico entre varios años.
     *
     * <p>Sirve tanto si cada año está en su propio dataset como si un dataset
     * trae varios: la unidad de comparación es el par dataset-año, deducido de
     * las fechas reales y no del nombre del fichero.
     *
     * <p>Si los datasets no son comparables devuelve
     * {@code comparable = false} con el motivo, nunca una comparación a medias
     * que parezca válida.
     */
    ComparacionInteranualResponseDto comparar(ComparacionInteranualRequestDto request);
}
