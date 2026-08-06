package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.AplicacionDashboardIlqDto;
import com.preventiva.backend.dto.CompatibilidadDashboardIlqDto;

public interface PlantillaDashboardIlqService {

    /** Si el dataset tiene los campos que la plantilla necesita, y si ya está aplicada. */
    CompatibilidadDashboardIlqDto comprobarCompatibilidad(Long datasetId);

    /**
     * Crea o actualiza el panel clínico de ILQ con sus 14 métricas y 14 widgets.
     * Idempotente: reaplicar no duplica nada.
     */
    AplicacionDashboardIlqDto aplicar(Long datasetId);
}
