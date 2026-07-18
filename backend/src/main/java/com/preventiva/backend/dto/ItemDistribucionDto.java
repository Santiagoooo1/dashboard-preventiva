package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ItemDistribucionDto {

    private String etiqueta;
    private Long valor;
}
