package com.preventiva.backend.dto;

import com.preventiva.backend.enums.TipoResultadoWidget;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PanelMetricaConfiguracionWidgetRequestDto {

    private TipoResultadoWidget tipoResultado;
    private ConfiguracionWidgetDto configuracionWidget;
}
