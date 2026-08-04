package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Página de pacientes o de registros, con el total REAL del subconjunto. */
@Getter
@Setter
@Builder
public class SubconjuntoPaginaDto {

    private List<FilaSubconjuntoDto> contenido;
    private int pagina;
    private int tamano;
    private long totalElementos;
    private int totalPaginas;
    private List<ColumnaSubconjuntoDto> columnas;
}
