import { apiDelete, apiGet, apiPost, apiPut } from './apiClient'
import type {
  CampoClinicoRequestDto,
  CampoClinicoResponseDto,
  DatasetClinicoRequestDto,
  DatasetClinicoResponseDto,
  DatasetFrontendMetadataResponseDto,
} from './types'

export function listarDatasets(signal?: AbortSignal): Promise<DatasetClinicoResponseDto[]> {
  return apiGet<DatasetClinicoResponseDto[]>('/datasets-clinicos', signal)
}

export function obtenerDataset(
  id: string | number,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiGet<DatasetClinicoResponseDto>(`/datasets-clinicos/${id}`, signal)
}

export function crearDataset(
  payload: DatasetClinicoRequestDto,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiPost<DatasetClinicoResponseDto>('/datasets-clinicos', payload, signal)
}

export function actualizarDataset(
  id: string | number,
  payload: DatasetClinicoRequestDto,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiPut<DatasetClinicoResponseDto>(`/datasets-clinicos/${id}`, payload, signal)
}

export function eliminarDataset(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/datasets-clinicos/${id}`, signal)
}

export function listarCampos(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<CampoClinicoResponseDto[]> {
  return apiGet<CampoClinicoResponseDto[]>(`/datasets-clinicos/${datasetId}/campos`, signal)
}

export function crearCampo(
  datasetId: string | number,
  payload: CampoClinicoRequestDto,
  signal?: AbortSignal,
): Promise<CampoClinicoResponseDto> {
  return apiPost<CampoClinicoResponseDto>(`/datasets-clinicos/${datasetId}/campos`, payload, signal)
}

export function actualizarCampo(
  datasetId: string | number,
  campoId: string | number,
  payload: CampoClinicoRequestDto,
  signal?: AbortSignal,
): Promise<CampoClinicoResponseDto> {
  return apiPut<CampoClinicoResponseDto>(`/datasets-clinicos/${datasetId}/campos/${campoId}`, payload, signal)
}

export function eliminarCampo(
  datasetId: string | number,
  campoId: string | number,
  signal?: AbortSignal,
): Promise<void> {
  return apiDelete(`/datasets-clinicos/${datasetId}/campos/${campoId}`, signal)
}

export function obtenerFrontendMetadata(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<DatasetFrontendMetadataResponseDto> {
  return apiGet<DatasetFrontendMetadataResponseDto>(`/datasets-clinicos/${datasetId}/frontend-metadata`, signal)
}
