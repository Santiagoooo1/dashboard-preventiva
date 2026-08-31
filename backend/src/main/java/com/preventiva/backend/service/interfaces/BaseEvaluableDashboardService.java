package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.BaseEvaluableDashboardDto;

public interface BaseEvaluableDashboardService {

    /**
     * Sobre qué población deben calcularse los indicadores de actividad del
     * dashboard inicial de este dataset.
     *
     * <p>Es una consulta: no crea ni modifica nada. Y afecta solo a cómo se
     * compone el dashboard inicial — el motor de métricas y la creación manual
     * siguen contando sobre todo lo que hay, que es lo que el usuario espera
     * cuando escribe un conteo a mano.
     */
    BaseEvaluableDashboardDto resolver(Long datasetId);
}
