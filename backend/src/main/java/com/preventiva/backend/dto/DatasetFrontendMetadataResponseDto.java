package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class DatasetFrontendMetadataResponseDto {

    private DatasetClinicoResponseDto dataset;
    private List<CampoClinicoMetadataDto> campos;
    private List<String> camposFiltrables;
    private List<String> camposNumericos;
    private List<String> camposAgrupables;
    private List<String> camposFecha;
    private List<MetricaClinicaResponseDto> metricas;
    private List<PanelClinicoResponseDto> paneles;
    private List<PlantillaImportacionResponseDto> plantillasImportacion;
    private ResumenConfiguracionDatasetDto resumenConfiguracion;
}
