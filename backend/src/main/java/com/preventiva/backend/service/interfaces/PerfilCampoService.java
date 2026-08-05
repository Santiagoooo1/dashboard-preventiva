package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.PerfilCamposResponseDto;

public interface PerfilCampoService {

    /** Perfil analítico de todas las columnas activas de un dataset. */
    PerfilCamposResponseDto obtenerPerfilCampos(Long datasetId);
}
