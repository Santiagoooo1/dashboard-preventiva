package com.preventiva.backend.service.interfaces;

import com.preventiva.backend.dto.DatasetFrontendMetadataResponseDto;
import com.preventiva.backend.dto.MetadataMetricasResponseDto;

public interface DatasetFrontendMetadataService {

    DatasetFrontendMetadataResponseDto obtenerMetadataDataset(Long datasetId);

    MetadataMetricasResponseDto obtenerMetadataMetricas(Long datasetId);
}
