package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class FilaImportacionTrabajoResponseDto {

    private Long id;
    private Integer numeroFilaOriginal;
    private Boolean excluida;
    private Map<String, String> valoresOriginales;
    private Map<String, String> valoresCorregidos;
    private List<ErrorImportacionTrabajoDto> errores;
}
