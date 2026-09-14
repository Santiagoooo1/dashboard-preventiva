package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PaginaInformeResponseDto {

    private Long id;
    private Integer orden;
    private String orientacion;
    private List<BloqueInformeResponseDto> bloques;
}
