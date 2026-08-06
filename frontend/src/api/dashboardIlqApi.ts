import { apiGet, apiPost } from './apiClient'
import type { AplicacionDashboardIlqDto, CompatibilidadDashboardIlqDto } from './types'

/**
 * Plantilla de dashboard clínico de ILQ (Fase 6.9I.4).
 *
 * La comprobación es de solo lectura y puede llamarse siempre; aplicar exige
 * una acción explícita del usuario y nunca se dispara sola tras importar.
 */
export function comprobarCompatibilidadIlq(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<CompatibilidadDashboardIlqDto> {
  return apiGet<CompatibilidadDashboardIlqDto>(
    `/datasets-clinicos/${datasetId}/dashboard-ilq/compatibilidad`,
    signal,
  )
}

export function aplicarDashboardIlq(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<AplicacionDashboardIlqDto> {
  return apiPost<AplicacionDashboardIlqDto>(`/datasets-clinicos/${datasetId}/dashboard-ilq`, {}, signal)
}
