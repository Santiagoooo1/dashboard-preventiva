package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** Fila de paciente o de registro. `valores` va indexado por código de columna. */
@Getter
@Setter
@Builder
public class FilaSubconjuntoDto {

    /** Identificador individual (vista Pacientes) o id técnico del registro. */
    private String clave;
    /** Solo en la vista Pacientes: cuántos registros del subconjunto agrupa. */
    private Integer numeroRegistros;
    private Map<String, Object> valores;
}
