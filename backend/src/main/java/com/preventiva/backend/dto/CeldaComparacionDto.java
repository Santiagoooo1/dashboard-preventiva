package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Un valor de la matriz interanual: lo que vale un periodo de un año.
 *
 * <p>Conserva numerador y denominador aunque la tabla compacta enseñe solo el
 * porcentaje. Comparar un 1,8 % sin saber si sale de 4/222 o de 1/55 es
 * comparar ruido: el tamaño de la población es parte del dato.
 */
@Getter
@Setter
@Builder
public class CeldaComparacionDto {

    /** «01».. «12» o «T1».. «T4»: comparable entre años, no incluye el año. */
    private String periodo;

    private Double valor;
    private Long numerador;
    private Long denominador;

    /** OK o SIN_BASE_EVALUABLE: un periodo sin población no es un cero. */
    private String estado;

    /** Reparto por categoría; solo en comparaciones de DISTRIBUCION. */
    private List<ValorCategoriaDto> categorias;

    /** Solo en RESUMEN_NUMERICO: los estadísticos del periodo. */
    private Double media;
    private Double minimo;
    private Double maximo;
}
