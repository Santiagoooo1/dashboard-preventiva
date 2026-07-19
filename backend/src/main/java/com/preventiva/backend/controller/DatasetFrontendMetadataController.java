package com.preventiva.backend.controller;

import com.preventiva.backend.dto.DatasetFrontendMetadataResponseDto;
import com.preventiva.backend.dto.MetadataMetricasResponseDto;
import com.preventiva.backend.service.interfaces.DatasetFrontendMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DatasetFrontendMetadataController {

    private final DatasetFrontendMetadataService datasetFrontendMetadataService;

    @GetMapping("/api/datasets-clinicos/{datasetId}/frontend-metadata")
    public DatasetFrontendMetadataResponseDto obtenerMetadataDataset(@PathVariable("datasetId") Long datasetId) {
        return datasetFrontendMetadataService.obtenerMetadataDataset(datasetId);
    }

    @GetMapping("/api/datasets-clinicos/{datasetId}/metadata-metricas")
    public MetadataMetricasResponseDto obtenerMetadataMetricas(@PathVariable("datasetId") Long datasetId) {
        return datasetFrontendMetadataService.obtenerMetadataMetricas(datasetId);
    }
}
