package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class OperadorFiltroCatalogoDto {

    private String codigo;
    private String nombre;
    private List<String> tiposDatoCompatibles;
    private Boolean requiereValor;
    private Boolean requiereLista;
}
