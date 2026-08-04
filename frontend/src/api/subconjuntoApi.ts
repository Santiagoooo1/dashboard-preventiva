import { apiPost } from './apiClient'
import type {
  SubconjuntoPaginaDto,
  SubconjuntoPerfilDto,
  SubconjuntoRequestDto,
  SubconjuntoResumenDto,
} from './types'

/**
 * Detalle del subconjunto activo (Fase 6.9H.3). Cuatro llamadas separadas para
 * poder cargar solo lo necesario: al seleccionar una barra basta el resumen; las
 * tablas y el perfil se piden cuando el usuario abre el panel.
 */

export function resumenSubconjunto(
  panelId: string | number,
  request: SubconjuntoRequestDto,
  signal?: AbortSignal,
): Promise<SubconjuntoResumenDto> {
  return apiPost<SubconjuntoResumenDto>(`/paneles-clinicos/${panelId}/subconjunto/resumen`, request, signal)
}

export function pacientesSubconjunto(
  panelId: string | number,
  request: SubconjuntoRequestDto,
  signal?: AbortSignal,
): Promise<SubconjuntoPaginaDto> {
  return apiPost<SubconjuntoPaginaDto>(`/paneles-clinicos/${panelId}/subconjunto/pacientes`, request, signal)
}

export function registrosSubconjunto(
  panelId: string | number,
  request: SubconjuntoRequestDto,
  signal?: AbortSignal,
): Promise<SubconjuntoPaginaDto> {
  return apiPost<SubconjuntoPaginaDto>(`/paneles-clinicos/${panelId}/subconjunto/registros`, request, signal)
}

export function perfilSubconjunto(
  panelId: string | number,
  request: SubconjuntoRequestDto,
  signal?: AbortSignal,
): Promise<SubconjuntoPerfilDto> {
  return apiPost<SubconjuntoPerfilDto>(`/paneles-clinicos/${panelId}/subconjunto/perfil`, request, signal)
}
