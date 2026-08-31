package com.preventiva.backend.service.impl;

import com.preventiva.backend.dto.PropuestaWidgetDto;
import com.preventiva.backend.entity.CampoClinico;
import com.preventiva.backend.enums.PrioridadDashboardCampo;
import com.preventiva.backend.repository.CampoClinicoRepository;
import com.preventiva.backend.repository.DatasetClinicoRepository;
import com.preventiva.backend.service.interfaces.BloqueInicialIlqService;
import com.preventiva.backend.util.BloqueInicialIlq;
import com.preventiva.backend.util.BloqueInicialIlq.ElementoInicial;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decide qué indicadores de infección quirúrgica abren el dashboard inicial de
 * un dataset (Fase 6.9L).
 *
 * <p>La regla es la presencia de los campos, no su contenido. Es deliberado:
 * descartar «Localización de la infección» por tener muchos nulos sería el
 * error clásico de esta vigilancia, porque un paciente que no se infecta no
 * tiene localización. Ese campo está casi vacío por diseño, y sin embargo es de
 * los más importantes —dentro de los casos con ILQ, que es donde el widget lo
 * mira—.
 */
@Service
@RequiredArgsConstructor
public class BloqueInicialIlqServiceImpl implements BloqueInicialIlqService {

    private final DatasetClinicoRepository datasetClinicoRepository;
    private final CampoClinicoRepository campoClinicoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PropuestaWidgetDto> proponerBloqueInicial(Long datasetId) {
        if (!datasetClinicoRepository.existsById(datasetId)) {
            throw new NoSuchElementException("No existe el dataset clínico con id: " + datasetId);
        }

        Set<String> disponibles = campoClinicoRepository.findByDatasetIdAndActivoTrue(datasetId).stream()
                // Un campo marcado como EXCLUIR es una decisión del usuario sobre
                // qué no quiere ver; se respeta también aquí.
                .filter(c -> c.getPrioridadDashboard() != PrioridadDashboardCampo.EXCLUIR)
                .map(CampoClinico::getCodigo)
                .collect(Collectors.toSet());

        // Sin el campo de infección esto no es un dataset de ILQ: los widgets de
        // profilaxis por su cuenta no son el bloque que se pidió, y forzarlos
        // convertiría cualquier dataset con una columna «adecuación» en algo que
        // aparenta vigilancia de infección quirúrgica.
        if (!contiene(disponibles, BloqueInicialIlq.CAMPO_IMPRESCINDIBLE)) {
            return List.of();
        }

        List<PropuestaWidgetDto> propuestas = new ArrayList<>();
        int orden = 1;

        for (ElementoInicial elemento : BloqueInicialIlq.elementos()) {
            boolean tieneTodo = elemento.camposNecesarios().stream().allMatch(c -> contiene(disponibles, c));
            if (!tieneTodo) {
                continue;
            }

            propuestas.add(PropuestaWidgetDto.builder()
                    .codigoMetrica(elemento.codigo())
                    .nombre(elemento.nombre())
                    .descripcion(elemento.descripcion())
                    .tipoMetrica(elemento.tipoMetrica().name())
                    .configuracion(elemento.configuracion())
                    .unidad(elemento.unidad())
                    .decimales(elemento.decimales())
                    .campoOrigen(elemento.camposNecesarios().get(0))
                    .motivo(elemento.motivo())
                    // Prioridad decreciente para que el orden sobreviva a
                    // cualquier reordenación posterior por puntuación.
                    .prioridad(1000 - orden)
                    .tipoVisualizacion(elemento.tipoVisualizacion().name())
                    .ancho(elemento.ancho())
                    .orden(orden)
                    .tipoResultado(elemento.tipoResultado().name())
                    .configuracionWidget(elemento.configuracionWidget())
                    .build());
            orden++;
        }

        return propuestas;
    }

    /** Los códigos canónicos se comparan sin distinguir mayúsculas, igual que en el resto del sistema. */
    private boolean contiene(Set<String> disponibles, String codigo) {
        return disponibles.stream().anyMatch(c -> c.equalsIgnoreCase(codigo));
    }
}
