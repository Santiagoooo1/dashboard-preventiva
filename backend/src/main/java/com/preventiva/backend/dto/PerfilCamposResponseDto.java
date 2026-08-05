package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PerfilCamposResponseDto {

    private DatasetClinicoResponseDto dataset;

    private long totalRegistros;

    /** Un perfil por campo ACTIVO. Los inactivos no admiten métricas nuevas. */
    private List<PerfilCampoDto> campos;

    /** Código del campo que identifica al individuo, si el dataset tiene uno. */
    private String campoIndividuo;

    /** A partir de cuántos valores distintos un texto deja de tratarse como categoría. */
    private int umbralCardinalidadCategorica;

    /** A partir de cuántas categorías el donut deja de ofrecerse. */
    private int maxCategoriasDonut;
}
