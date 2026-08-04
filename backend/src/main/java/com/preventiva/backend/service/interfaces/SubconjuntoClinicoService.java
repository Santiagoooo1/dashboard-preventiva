package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.SubconjuntoPaginaDto;
import com.preventiva.backend.dto.SubconjuntoPerfilDto;
import com.preventiva.backend.dto.SubconjuntoRequestDto;
import com.preventiva.backend.dto.SubconjuntoResumenDto;

/**
 * Detalle clínico del subconjunto activo (Fase 6.9H.3): permite inspeccionar
 * qué pacientes y qué registros forman el resultado que el dashboard está
 * mostrando, sin descargar el subconjunto entero al navegador.
 *
 * Las cuatro vistas se sirven por separado a propósito: al crear una selección
 * solo se pide el resumen (barato), y las tablas o el perfil se calculan
 * únicamente si el usuario abre el detalle.
 */
public interface SubconjuntoClinicoService {

    SubconjuntoResumenDto resumen(Long panelId, SubconjuntoRequestDto request);

    SubconjuntoPaginaDto pacientes(Long panelId, SubconjuntoRequestDto request);

    SubconjuntoPaginaDto registros(Long panelId, SubconjuntoRequestDto request);

    SubconjuntoPerfilDto perfil(Long panelId, SubconjuntoRequestDto request);
}
