package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Cuántos casos tuvo una categoría en un periodo concreto. */
@Getter
@Setter
@Builder
public class ValorCategoriaDto {

    private String categoria;
    private Double valor;
}
