package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.CatalogoInformeResponseDto;

public interface CatalogoInformeService {

    /**
     * Todo lo insertable en un informe: los widgets ya configurados de los
     * dashboards y, aparte, las métricas que aún no están en ninguno.
     *
     * <p>Una sola llamada y sin ejecutar ninguna métrica.
     */
    CatalogoInformeResponseDto obtener();
}
