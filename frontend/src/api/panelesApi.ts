import { apiDelete, apiGet, apiPost, apiPut } from './apiClient'
import type {
  DashboardPanelMetadataResponseDto,
  PanelClinicoRequestDto,
  PanelClinicoResponseDto,
  PanelMetricaConfiguracionWidgetRequestDto,
  PanelMetricaRequestDto,
  PanelMetricaResponseDto,
} from './types'

export function listarPaneles(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<PanelClinicoResponseDto[]> {
  return apiGet<PanelClinicoResponseDto[]>(`/datasets-clinicos/${datasetId}/paneles`, signal)
}

export function obtenerPanel(id: string | number, signal?: AbortSignal): Promise<PanelClinicoResponseDto> {
  return apiGet<PanelClinicoResponseDto>(`/paneles-clinicos/${id}`, signal)
}

export function crearPanel(
  datasetId: string | number,
  payload: PanelClinicoRequestDto,
  signal?: AbortSignal,
): Promise<PanelClinicoResponseDto> {
  return apiPost<PanelClinicoResponseDto>(`/datasets-clinicos/${datasetId}/paneles`, payload, signal)
}

export function actualizarPanel(
  id: string | number,
  payload: PanelClinicoRequestDto,
  signal?: AbortSignal,
): Promise<PanelClinicoResponseDto> {
  return apiPut<PanelClinicoResponseDto>(`/paneles-clinicos/${id}`, payload, signal)
}

export function desactivarPanel(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/paneles-clinicos/${id}`, signal)
}

export function listarWidgets(
  panelId: string | number,
  signal?: AbortSignal,
): Promise<PanelMetricaResponseDto[]> {
  return apiGet<PanelMetricaResponseDto[]>(`/paneles-clinicos/${panelId}/metricas`, signal)
}

export function anadirWidget(
  panelId: string | number,
  payload: PanelMetricaRequestDto,
  signal?: AbortSignal,
): Promise<PanelMetricaResponseDto> {
  return apiPost<PanelMetricaResponseDto>(`/paneles-clinicos/${panelId}/metricas`, payload, signal)
}

export function actualizarWidget(
  panelId: string | number,
  panelMetricaId: string | number,
  payload: PanelMetricaRequestDto,
  signal?: AbortSignal,
): Promise<PanelMetricaResponseDto> {
  return apiPut<PanelMetricaResponseDto>(`/paneles-clinicos/${panelId}/metricas/${panelMetricaId}`, payload, signal)
}

export function quitarWidget(
  panelId: string | number,
  panelMetricaId: string | number,
  signal?: AbortSignal,
): Promise<void> {
  return apiDelete(`/paneles-clinicos/${panelId}/metricas/${panelMetricaId}`, signal)
}

export function actualizarConfiguracionWidget(
  panelId: string | number,
  panelMetricaId: string | number,
  payload: PanelMetricaConfiguracionWidgetRequestDto,
  signal?: AbortSignal,
): Promise<PanelMetricaResponseDto> {
  return apiPut<PanelMetricaResponseDto>(
    `/paneles-clinicos/${panelId}/metricas/${panelMetricaId}/configuracion-widget`,
    payload,
    signal,
  )
}

export function obtenerDashboardMetadata(
  panelId: string | number,
  signal?: AbortSignal,
): Promise<DashboardPanelMetadataResponseDto> {
  return apiGet<DashboardPanelMetadataResponseDto>(`/paneles-clinicos/${panelId}/dashboard-metadata`, signal)
}
