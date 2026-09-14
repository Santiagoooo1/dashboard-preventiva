package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.BloqueInformeRequestDto;
import com.preventiva.backend.dto.BloqueInformeResponseDto;
import com.preventiva.backend.dto.InformeClinicoRequestDto;
import com.preventiva.backend.dto.InformeClinicoResponseDto;
import com.preventiva.backend.dto.PaginaInformeResponseDto;

import java.util.List;

public interface InformeClinicoService {

    List<InformeClinicoResponseDto> listar();

    /**
     * Informe con sus páginas y bloques, SIN calcular resultados.
     *
     * <p>Es lo que necesita el editor mientras se monta el documento: la
     * estructura, no las cifras.
     */
    InformeClinicoResponseDto obtenerEstructura(Long id);

    /**
     * Informe con cada bloque analítico ya resuelto.
     *
     * <p>Una sola llamada para todo el documento. Los bloques que apuntan al
     * mismo recurso comparten resultado en vez de pedirlo dos veces.
     */
    InformeClinicoResponseDto obtenerConResultados(Long id);

    InformeClinicoResponseDto crear(InformeClinicoRequestDto request);

    InformeClinicoResponseDto actualizar(Long id, InformeClinicoRequestDto request);

    void eliminar(Long id);

    // --- Páginas ---

    PaginaInformeResponseDto anadirPagina(Long informeId);

    PaginaInformeResponseDto duplicarPagina(Long informeId, Long paginaId);

    void eliminarPagina(Long informeId, Long paginaId);

    /** Coloca la página en la posición indicada; el resto se renumera. */
    InformeClinicoResponseDto moverPagina(Long informeId, Long paginaId, int nuevaPosicion);

    // --- Bloques ---

    BloqueInformeResponseDto anadirBloque(Long informeId, Long paginaId, BloqueInformeRequestDto request);

    BloqueInformeResponseDto actualizarBloque(
            Long informeId, Long paginaId, Long bloqueId, BloqueInformeRequestDto request);

    void eliminarBloque(Long informeId, Long paginaId, Long bloqueId);

    /** Coloca el bloque en la posición indicada dentro de su página. */
    PaginaInformeResponseDto moverBloque(Long informeId, Long paginaId, Long bloqueId, int nuevaPosicion);
}
