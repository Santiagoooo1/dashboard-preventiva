package com.preventiva.backend.dto;

import com.preventiva.backend.enums.Granularidad;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfiguracionWidgetDto {

    private Granularidad granularidad;
    private String campoFecha;
    private String campoSegmentacion;
    private String campoAgrupacion;
}
