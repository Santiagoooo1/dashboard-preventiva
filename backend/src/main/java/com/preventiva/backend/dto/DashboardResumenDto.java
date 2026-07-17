package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardResumenDto {
    private Long totalCirugias;
    private Long totalIlq;
    private Double tasaIlq;
    private Long profilaxisAdecuadas;
    private Long profilaxisInadecuadas;
    private Long profilaxisSinDato;
    private Double tasaAdecuacionProfilaxis;
}
