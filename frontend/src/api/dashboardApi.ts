import { apiPost } from './apiClient'
import type { DashboardPanelResponseDto } from './types'

export function ejecutarDashboard(
  panelId: string | number,
  signal?: AbortSignal,
): Promise<DashboardPanelResponseDto> {
  return apiPost<DashboardPanelResponseDto>(`/paneles-clinicos/${panelId}/dashboard`, {}, signal)
}
