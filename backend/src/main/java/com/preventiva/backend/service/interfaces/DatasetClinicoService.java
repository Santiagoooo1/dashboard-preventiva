package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DatasetClinicoRequestDto;
import com.preventiva.backend.dto.DatasetClinicoResponseDto;
import com.preventiva.backend.dto.ReanudarBorradorDatasetDto;

import java.util.List;

public interface DatasetClinicoService {

    /**
     * @param incluirBorradores si es false (comportamiento por defecto), excluye los
     *                          datasets cuyo estadoDataset no sea ACTIVO (o nulo, para
     *                          los creados antes de este campo).
     */
    List<DatasetClinicoResponseDto> listar(boolean incluirBorradores);

    DatasetClinicoResponseDto obtenerPorId(Long id);

    DatasetClinicoResponseDto crear(DatasetClinicoRequestDto request);

    DatasetClinicoResponseDto actualizar(Long id, DatasetClinicoRequestDto request);

    void desactivar(Long id);

    /** Promueve el dataset a ACTIVO: se llama cuando su importación guiada se completa con éxito. */
    DatasetClinicoResponseDto activar(Long id);

    /**
     * Borra un dataset BORRADOR o VALIDANDO junto con sus dependencias
     * temporales (campos, plantillas, mapeos, copias de trabajo e
     * importaciones asociadas). Rechaza datasets con registros clínicos
     * reales, sea cual sea su estado.
     */
    void descartarBorrador(Long id);

    /**
     * Determina si un dataset BORRADOR/VALIDANDO puede reanudarse desde el
     * asistente guiado, y en qué paso: busca la copia de trabajo más
     * relevante (en edición, lista para importar o ya importada) y, si no
     * hay ninguna, la configuración parcial existente (plantillas/campos).
     */
    ReanudarBorradorDatasetDto reanudarBorrador(Long id);
}
