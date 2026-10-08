import { apiDelete, apiGet, apiGetArchivo, apiPost, apiPut } from './apiClient'
import type {
  BloqueInformeRequestDto,
  CatalogoInformeResponseDto,
  BloqueInformeResponseDto,
  InformeClinicoRequestDto,
  InformeClinicoResponseDto,
  PaginaInformeResponseDto,
} from './types'

/**
 * Qué se puede insertar en un informe, en una sola llamada.
 *
 * Trae los widgets de los dashboards con su configuración y, aparte, las
 * métricas que no están en ninguno. No ejecuta ningún cálculo.
 */
export function obtenerCatalogoInforme(signal?: AbortSignal): Promise<CatalogoInformeResponseDto> {
  return apiGet<CatalogoInformeResponseDto>('/informes/catalogo', signal)
}

/**
 * PDF del informe, generado por el backend (Fase 6.9R.2).
 *
 * <p>El frontend solo manda el id: no pide `/resultados`, no maqueta nada y no
 * transforma ningún SVG. Es el navegador headless del servidor el que abre la
 * ruta de impresión canónica.
 */
export function descargarInformePdf(
  id: string | number,
  signal?: AbortSignal,
): Promise<{ blob: Blob; nombre: string | null }> {
  return apiGetArchivo(`/informes/${id}/pdf`, signal)
}

export function listarInformes(signal?: AbortSignal): Promise<InformeClinicoResponseDto[]> {
  return apiGet<InformeClinicoResponseDto[]>('/informes', signal)
}

/** Estructura del informe, sin calcular resultados: lo que necesita el editor. */
export function obtenerInforme(
  id: string | number,
  signal?: AbortSignal,
): Promise<InformeClinicoResponseDto> {
  return apiGet<InformeClinicoResponseDto>(`/informes/${id}`, signal)
}

/**
 * Informe con cada bloque analítico ya resuelto.
 *
 * Una sola llamada para todo el documento: los bloques no piden sus datos por
 * separado, y los que apuntan al mismo indicador comparten resultado.
 */
export function obtenerInformeConResultados(
  id: string | number,
  signal?: AbortSignal,
): Promise<InformeClinicoResponseDto> {
  return apiGet<InformeClinicoResponseDto>(`/informes/${id}/resultados`, signal)
}

export function crearInforme(
  payload: InformeClinicoRequestDto,
  signal?: AbortSignal,
): Promise<InformeClinicoResponseDto> {
  return apiPost<InformeClinicoResponseDto>('/informes', payload, signal)
}

export function actualizarInforme(
  id: string | number,
  payload: InformeClinicoRequestDto,
  signal?: AbortSignal,
): Promise<InformeClinicoResponseDto> {
  return apiPut<InformeClinicoResponseDto>(`/informes/${id}`, payload, signal)
}

export function eliminarInforme(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/informes/${id}`, signal)
}

// --- Páginas ---

export function anadirPagina(
  informeId: string | number,
  signal?: AbortSignal,
): Promise<PaginaInformeResponseDto> {
  return apiPost<PaginaInformeResponseDto>(`/informes/${informeId}/paginas`, undefined, signal)
}

export function duplicarPagina(
  informeId: string | number,
  paginaId: string | number,
  signal?: AbortSignal,
): Promise<PaginaInformeResponseDto> {
  return apiPost<PaginaInformeResponseDto>(
    `/informes/${informeId}/paginas/${paginaId}/duplicar`,
    undefined,
    signal,
  )
}

export function eliminarPagina(
  informeId: string | number,
  paginaId: string | number,
  signal?: AbortSignal,
): Promise<void> {
  return apiDelete(`/informes/${informeId}/paginas/${paginaId}`, signal)
}

// --- Bloques ---

export function anadirBloque(
  informeId: string | number,
  paginaId: string | number,
  payload: BloqueInformeRequestDto,
  signal?: AbortSignal,
): Promise<BloqueInformeResponseDto> {
  return apiPost<BloqueInformeResponseDto>(
    `/informes/${informeId}/paginas/${paginaId}/bloques`,
    payload,
    signal,
  )
}

export function actualizarBloque(
  informeId: string | number,
  paginaId: string | number,
  bloqueId: string | number,
  payload: BloqueInformeRequestDto,
  signal?: AbortSignal,
): Promise<BloqueInformeResponseDto> {
  return apiPut<BloqueInformeResponseDto>(
    `/informes/${informeId}/paginas/${paginaId}/bloques/${bloqueId}`,
    payload,
    signal,
  )
}

export function eliminarBloque(
  informeId: string | number,
  paginaId: string | number,
  bloqueId: string | number,
  signal?: AbortSignal,
): Promise<void> {
  return apiDelete(`/informes/${informeId}/paginas/${paginaId}/bloques/${bloqueId}`, signal)
}

export function moverBloque(
  informeId: string | number,
  paginaId: string | number,
  bloqueId: string | number,
  posicion: number,
  signal?: AbortSignal,
): Promise<PaginaInformeResponseDto> {
  return apiPut<PaginaInformeResponseDto>(
    `/informes/${informeId}/paginas/${paginaId}/bloques/${bloqueId}/posicion/${posicion}`,
    undefined,
    signal,
  )
}
