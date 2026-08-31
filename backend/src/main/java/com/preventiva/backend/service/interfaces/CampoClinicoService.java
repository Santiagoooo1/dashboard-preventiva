package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.CampoClinicoRequestDto;
import com.preventiva.backend.dto.CampoClinicoResponseDto;

import java.util.List;

public interface CampoClinicoService {

    List<CampoClinicoResponseDto> listarPorDataset(Long datasetId);

    CampoClinicoResponseDto crear(Long datasetId, CampoClinicoRequestDto request);

    /**
     * Deja listos los campos que una importación necesita: reutiliza los que ya
     * existan en el dataset y crea solo los que falten.
     *
     * <p>Existe porque {@link #crear} rechaza códigos repetidos —y debe seguir
     * haciéndolo: dos campos con el mismo código en un dataset es un error—,
     * pero una importación que se reanuda vuelve a declarar las mismas columnas
     * y eso NO es un error, es el caso normal.
     *
     * <p>Es idempotente: llamarla varias veces con la misma lista deja siempre
     * los mismos campos, con los mismos identificadores.
     *
     * @return un campo por cada solicitado, en el mismo orden
     */
    List<CampoClinicoResponseDto> asegurarParaImportacion(
            Long datasetId, List<CampoClinicoRequestDto> solicitados);

    CampoClinicoResponseDto actualizar(Long datasetId, Long campoId, CampoClinicoRequestDto request);

    void desactivar(Long datasetId, Long campoId);
}
