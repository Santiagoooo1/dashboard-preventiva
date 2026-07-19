import { apiGet } from './apiClient'
import type { DatasetClinicoResponseDto, DatasetFrontendMetadataResponseDto } from './types'

export function listarDatasets(signal?: AbortSignal): Promise<DatasetClinicoResponseDto[]> {
  return apiGet<DatasetClinicoResponseDto[]>('/datasets-clinicos', signal)
}

export function obtenerFrontendMetadata(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<DatasetFrontendMetadataResponseDto> {
  return apiGet<DatasetFrontendMetadataResponseDto>(`/datasets-clinicos/${datasetId}/frontend-metadata`, signal)
}
