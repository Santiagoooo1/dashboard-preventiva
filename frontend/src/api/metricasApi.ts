import { apiDelete, apiGet, apiPost, apiPut } from './apiClient'
import type {
  EjecucionMetricaRequestDto,
  MetadataMetricasResponseDto,
  MetricaClinicaRequestDto,
  MetricaClinicaResponseDto,
  PreviewMetricaRequestDto,
  ResultadoMetricaResponseDto,
} from './types'

export function listarMetricas(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<MetricaClinicaResponseDto[]> {
  return apiGet<MetricaClinicaResponseDto[]>(`/datasets-clinicos/${datasetId}/metricas`, signal)
}

export function obtenerMetrica(id: string | number, signal?: AbortSignal): Promise<MetricaClinicaResponseDto> {
  return apiGet<MetricaClinicaResponseDto>(`/metricas-clinicas/${id}`, signal)
}

export function obtenerMetadataMetricas(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<MetadataMetricasResponseDto> {
  return apiGet<MetadataMetricasResponseDto>(`/datasets-clinicos/${datasetId}/metadata-metricas`, signal)
}

export function crearMetrica(
  datasetId: string | number,
  payload: MetricaClinicaRequestDto,
  signal?: AbortSignal,
): Promise<MetricaClinicaResponseDto> {
  return apiPost<MetricaClinicaResponseDto>(`/datasets-clinicos/${datasetId}/metricas`, payload, signal)
}

export function actualizarMetrica(
  id: string | number,
  payload: MetricaClinicaRequestDto,
  signal?: AbortSignal,
): Promise<MetricaClinicaResponseDto> {
  return apiPut<MetricaClinicaResponseDto>(`/metricas-clinicas/${id}`, payload, signal)
}

export function desactivarMetrica(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/metricas-clinicas/${id}`, signal)
}

export function previewMetrica(
  datasetId: string | number,
  payload: PreviewMetricaRequestDto,
  signal?: AbortSignal,
): Promise<ResultadoMetricaResponseDto> {
  return apiPost<ResultadoMetricaResponseDto>(`/datasets-clinicos/${datasetId}/metricas/preview`, payload, signal)
}

export function ejecutarMetrica(
  id: string | number,
  request?: EjecucionMetricaRequestDto,
  signal?: AbortSignal,
): Promise<ResultadoMetricaResponseDto> {
  return apiPost<ResultadoMetricaResponseDto>(`/metricas-clinicas/${id}/ejecutar`, request ?? {}, signal)
}
