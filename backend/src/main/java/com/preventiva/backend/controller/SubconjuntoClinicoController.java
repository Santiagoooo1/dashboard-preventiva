package com.preventiva.backend.controller;

import com.preventiva.backend.dto.SubconjuntoPaginaDto;
import com.preventiva.backend.dto.SubconjuntoPerfilDto;
import com.preventiva.backend.dto.SubconjuntoRequestDto;
import com.preventiva.backend.dto.SubconjuntoResumenDto;
import com.preventiva.backend.service.interfaces.SubconjuntoClinicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Detalle del subconjunto activo (Fase 6.9H.3).
 *
 * Cuatro endpoints en vez de uno con parámetro `vista`: permiten carga
 * diferida real (al seleccionar una barra solo se pide el resumen, que es
 * barato) y dan respuestas tipadas en lugar de una unión que el frontend
 * tendría que discriminar. Todos comparten el mismo servicio y, por tanto, el
 * mismo motor de filtros que el dashboard.
 */
@RestController
@RequestMapping("/api/paneles-clinicos/{panelId}/subconjunto")
@RequiredArgsConstructor
public class SubconjuntoClinicoController {

    private final SubconjuntoClinicoService subconjuntoClinicoService;

    @PostMapping("/resumen")
    public SubconjuntoResumenDto resumen(
            @PathVariable("panelId") Long panelId,
            @RequestBody(required = false) SubconjuntoRequestDto request) {
        return subconjuntoClinicoService.resumen(panelId, request);
    }

    @PostMapping("/pacientes")
    public SubconjuntoPaginaDto pacientes(
            @PathVariable("panelId") Long panelId,
            @RequestBody(required = false) SubconjuntoRequestDto request) {
        return subconjuntoClinicoService.pacientes(panelId, request);
    }

    @PostMapping("/registros")
    public SubconjuntoPaginaDto registros(
            @PathVariable("panelId") Long panelId,
            @RequestBody(required = false) SubconjuntoRequestDto request) {
        return subconjuntoClinicoService.registros(panelId, request);
    }

    @PostMapping("/perfil")
    public SubconjuntoPerfilDto perfil(
            @PathVariable("panelId") Long panelId,
            @RequestBody(required = false) SubconjuntoRequestDto request) {
        return subconjuntoClinicoService.perfil(panelId, request);
    }
}
