package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TipoMetricaCatalogoDto {

    private String codigo;
    private String nombre;
    private String descripcion;
    private Boolean requiereCampoValor;
    private Boolean requiereCampoAgrupacion;
    private Boolean permiteFiltros;
    private Boolean permiteSerieTemporal;
    private Boolean permiteComparativa;
    private EstructuraConfiguracionMetricaDto estructuraConfiguracion;
    private Object ejemploConfiguracion;
}
