package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ComparativaResponseDto {

    private Long metricaId;
    private String codigo;
    private String tipoMetrica;
    private String agrupadoPor;
    private List<ItemComparativaDto> items;
}
