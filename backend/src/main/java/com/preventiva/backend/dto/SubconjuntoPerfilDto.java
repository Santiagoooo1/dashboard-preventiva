package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Perfil agregado del subconjunto. `baseCalculo` es explícito para que nunca se
 * confunda el denominador: aquí las distribuciones se calculan sobre REGISTROS,
 * no sobre pacientes únicos.
 */
@Getter
@Setter
@Builder
public class SubconjuntoPerfilDto {

    /** Siempre "REGISTROS" en esta fase. */
    private String baseCalculo;
    private long totalRegistros;
    private Long totalPacientesUnicos;
    private Double registrosPorPaciente;

    private List<PerfilNumericoDto> numericos;
    private List<PerfilCategoricoDto> categoricos;
    private List<PerfilCategoricoDto> booleanos;
    private List<PerfilFechaDto> fechas;
}
