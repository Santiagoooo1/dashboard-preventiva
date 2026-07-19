package com.preventiva.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class MetadataMetricasResponseDto {

    private DatasetClinicoResponseDto dataset;
    private List<CampoMetricaMetadataDto> campos;
}
