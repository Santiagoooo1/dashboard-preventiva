package com.preventiva.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExcluirSimilaresRequestDto {

    private String tipoError;
    private String nombreColumna;
}
