import { apiGet } from './apiClient'
import type { PropuestaDashboardResponseDto } from './types'

/**
 * Qué widgets propondría el dashboard recomendado (Fase 6.9I.4.1).
 *
 * Solo lectura: no crea nada. El usuario ve la propuesta, desmarca lo que no
 * quiera y la creación se hace con los endpoints normales de métricas y
 * widgets.
 */
export function obtenerPropuestaDashboard(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<PropuestaDashboardResponseDto> {
  return apiGet<PropuestaDashboardResponseDto>(
    `/datasets-clinicos/${datasetId}/dashboard-recomendado/propuesta`,
    signal,
  )
}
