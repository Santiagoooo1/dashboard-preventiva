package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PerfilCategoricoDto {

    private String campo;
    private String etiqueta;
    private String tipoDato;
    private long valoresValidos;
    private long valoresAusentes;
    /** Categorías principales; el resto se agrupa solo en presentación. */
    private List<CategoriaPerfilDto> categorias;
    private long otrasCategorias;
}
