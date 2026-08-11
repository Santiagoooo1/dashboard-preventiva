package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class CampoMetricaMetadataDto {

    private String codigo;
    private String etiqueta;
    private String tipoDato;
    private Boolean esComun;
    private List<String> operadoresCompatibles;
    private Boolean utilizableComoCampoValor;
    private Boolean utilizableComoCampoAgrupacion;
    private Boolean utilizableComoCampoFecha;

    /** Relevancia para dashboards, independiente de esComun y obligatorio. */
    private String prioridadDashboard;
}
