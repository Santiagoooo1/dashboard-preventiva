package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MetricaClinicaResponseDto {

    private Long id;
    private Long datasetId;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String tipoMetrica;
    private ConfiguracionMetricaDto configuracion;
    private String unidad;
    private Integer decimales;
    private Integer orden;
    private Boolean activa;
}
