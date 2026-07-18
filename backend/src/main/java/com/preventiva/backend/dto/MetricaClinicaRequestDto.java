package com.preventiva.backend.dto;

import com.preventiva.backend.enums.TipoMetrica;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MetricaClinicaRequestDto {

    @NotBlank
    private String codigo;

    @NotBlank
    private String nombre;

    private String descripcion;

    @NotNull
    private TipoMetrica tipoMetrica;

    @NotNull
    private ConfiguracionMetricaDto configuracion;

    private String unidad;

    private Integer decimales;

    private Integer orden;
}
