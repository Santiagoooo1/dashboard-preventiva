package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.PropuestaWidgetDto;

import java.util.List;

public interface BloqueInicialIlqService {

    /**
     * Indicadores de infección quirúrgica que deben abrir el dashboard inicial
     * de este dataset, ya en el orden en que se ven.
     *
     * <p>Devuelve lista vacía cuando el dataset no habla de infección
     * quirúrgica: entonces el dashboard inicial se genera solo con las reglas
     * genéricas, como siempre. Si el dataset sí es de ILQ pero le falta algún
     * campo secundario, se omite únicamente el widget que lo necesita.
     *
     * <p>Es una consulta: no crea métricas ni paneles. Quien decide crearlos es
     * el generador del dashboard inicial.
     */
    List<PropuestaWidgetDto> proponerBloqueInicial(Long datasetId);
}
