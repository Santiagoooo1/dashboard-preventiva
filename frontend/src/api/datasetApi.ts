import { apiDelete, apiGet, apiPost, apiPut } from './apiClient'
import type {
  CampoClinicoRequestDto,
  CampoClinicoResponseDto,
  DatasetClinicoRequestDto,
  DatasetClinicoResponseDto,
  DatasetFrontendMetadataResponseDto,
  ReanudarBorradorDatasetDto,
} from './types'

export function listarDatasets(
  incluirBorradores = false,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto[]> {
  return apiGet<DatasetClinicoResponseDto[]>(
    `/datasets-clinicos?incluirBorradores=${incluirBorradores}`,
    signal,
  )
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

/** Promueve un dataset BORRADOR/VALIDANDO a ACTIVO (importación completada con éxito). */
export function activarDataset(
  id: string | number,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiPost<DatasetClinicoResponseDto>(`/datasets-clinicos/${id}/activar`, undefined, signal)
}

/** Borra un dataset BORRADOR/VALIDANDO y sus dependencias temporales. Rechaza si tiene registros reales. */
export function descartarDatasetBorrador(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/datasets-clinicos/${id}/descartar-borrador`, signal)
}

/** Determina si un dataset BORRADOR/VALIDANDO puede reanudarse en el asistente guiado, y en qué paso. */
export function reanudarDatasetBorrador(
  id: string | number,
  signal?: AbortSignal,
): Promise<ReanudarBorradorDatasetDto> {
  return apiGet<ReanudarBorradorDatasetDto>(`/datasets-clinicos/${id}/reanudar-borrador`, signal)
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
