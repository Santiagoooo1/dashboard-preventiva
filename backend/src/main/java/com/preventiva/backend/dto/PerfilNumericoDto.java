package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Estadísticos de un campo numérico. Se calculan en backend: la mediana exige
 *  ordenar TODOS los valores, y hacerlo en el navegador obligaría a descargar
 *  el subconjunto completo. */
@Getter
@Setter
@Builder
public class PerfilNumericoDto {

    private String campo;
    private String etiqueta;
    private long valoresValidos;
    private long valoresAusentes;
    private Double media;
    private Double mediana;
    private Double minimo;
    private Double maximo;
}
