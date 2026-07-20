import { apiPost } from './apiClient'
import type { DashboardPanelRequestDto, DashboardPanelResponseDto } from './types'

export function ejecutarDashboard(
  panelId: string | number,
  filtros?: DashboardPanelRequestDto,
  signal?: AbortSignal,
): Promise<DashboardPanelResponseDto> {
  return apiPost<DashboardPanelResponseDto>(`/paneles-clinicos/${panelId}/dashboard`, filtros ?? {}, signal)
}
