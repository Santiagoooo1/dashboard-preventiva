package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.PropuestaDashboardResponseDto;

public interface PropuestaDashboardService {

    /**
     * Widgets propuestos para el dashboard recomendado de un dataset, ordenados
     * por prioridad. No crea nada: es una propuesta que el usuario confirma.
     */
    PropuestaDashboardResponseDto proponer(Long datasetId);
}
