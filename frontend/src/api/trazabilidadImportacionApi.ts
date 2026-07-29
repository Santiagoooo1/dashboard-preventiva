import { apiGet } from './apiClient'
import type { TrazabilidadImportacionTrabajoResponseDto, ResumenTrazabilidadImportacionTrabajoDto } from './types'

export function obtenerTrazabilidadImportacionTrabajo(
  importacionTrabajoId: string | number,
  signal?: AbortSignal,
): Promise<TrazabilidadImportacionTrabajoResponseDto> {
  return apiGet<TrazabilidadImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${importacionTrabajoId}/trazabilidad`,
    signal,
  )
}

export function obtenerResumenTrazabilidadImportacionTrabajo(
  importacionTrabajoId: string | number,
  signal?: AbortSignal,
): Promise<ResumenTrazabilidadImportacionTrabajoDto> {
  return apiGet<ResumenTrazabilidadImportacionTrabajoDto>(
    `/importaciones-trabajo/${importacionTrabajoId}/trazabilidad/resumen`,
    signal,
  )
}

export function obtenerTrazabilidadPorImportacionGenerica(
  importacionGenericaId: string | number,
  signal?: AbortSignal,
): Promise<TrazabilidadImportacionTrabajoResponseDto> {
  return apiGet<TrazabilidadImportacionTrabajoResponseDto>(
    `/importaciones-genericas/${importacionGenericaId}/trazabilidad`,
    signal,
  )
}
