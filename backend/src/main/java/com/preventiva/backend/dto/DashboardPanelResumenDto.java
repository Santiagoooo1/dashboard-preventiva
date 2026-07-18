package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardPanelResumenDto {

    private Integer totalWidgets;
    private Integer widgetsOk;
    private Integer widgetsConError;
}
