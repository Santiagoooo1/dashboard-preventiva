import { apiDelete, apiGet, apiPost, apiPostFormData, apiPut } from './apiClient'
import type {
  CrearImportacionTrabajoResponseDto,
  ErrorImportacionTrabajoDto,
  ImportacionTrabajoResponseDto,
  ImportarDesdeTrabajoResponseDto,
  PaginaFilasImportacionTrabajoResponseDto,
  RevalidarImportacionTrabajoResponseDto,
} from './types'

export interface ImportacionTrabajoPayload {
  archivo: File
  plantillaId: number
  indiceHoja?: number
  filaCabecera?: number
}

export interface ListarFilasImportacionTrabajoParams {
  page?: number
  size?: number
  soloConErrores?: boolean
}

// Mismo criterio de tipo neutro que en importacionesApi.ts: evita que el
// backend detecte un .xls como CSV por su content-type de navegador.
function archivoConTipoNeutro(archivo: File): File {
  return new File([archivo], archivo.name, { type: 'application/octet-stream' })
}

function construirFormData(payload: ImportacionTrabajoPayload): FormData {
  const formData = new FormData()
  formData.append('archivo', archivoConTipoNeutro(payload.archivo), payload.archivo.name)
  formData.append('plantillaId', String(payload.plantillaId))
  formData.append('indiceHoja', String(payload.indiceHoja ?? 0))
  if (payload.filaCabecera !== undefined && payload.filaCabecera !== null) {
    formData.append('filaCabecera', String(payload.filaCabecera))
  }
  return formData
}

export function crearImportacionTrabajo(
  payload: ImportacionTrabajoPayload,
  signal?: AbortSignal,
): Promise<CrearImportacionTrabajoResponseDto> {
  return apiPostFormData<CrearImportacionTrabajoResponseDto>(
    '/importaciones-trabajo',
    construirFormData(payload),
    signal,
  )
}

export function obtenerImportacionTrabajo(
  id: string | number,
  signal?: AbortSignal,
): Promise<ImportacionTrabajoResponseDto> {
  return apiGet<ImportacionTrabajoResponseDto>(`/importaciones-trabajo/${id}`, signal)
}

export function listarFilasImportacionTrabajo(
  id: string | number,
  params: ListarFilasImportacionTrabajoParams = {},
  signal?: AbortSignal,
): Promise<PaginaFilasImportacionTrabajoResponseDto> {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 50),
    soloConErrores: String(params.soloConErrores ?? false),
  })
  return apiGet<PaginaFilasImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/filas?${query.toString()}`,
    signal,
  )
}

export function listarErroresImportacionTrabajo(
  id: string | number,
  signal?: AbortSignal,
): Promise<ErrorImportacionTrabajoDto[]> {
  return apiGet<ErrorImportacionTrabajoDto[]>(`/importaciones-trabajo/${id}/errores`, signal)
}

export function revalidarImportacionTrabajo(
  id: string | number,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(`/importaciones-trabajo/${id}/revalidar`, undefined, signal)
}

export function actualizarExclusionFilaTrabajo(
  id: string | number,
  numeroFila: number,
  excluida: boolean,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPut<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/filas/${numeroFila}/exclusion`,
    { excluida },
    signal,
  )
}

export function excluirSimilaresImportacionTrabajo(
  id: string | number,
  tipoError: string,
  nombreColumna: string,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/excluir-similares`,
    { tipoError, nombreColumna },
    signal,
  )
}

// Endpoint recomendado (columna en el body, no en la ruta): admite nombres de
// columna con "/", espacios o acentos. No usar el endpoint deprecated con
// columna en la ruta.
export function corregirCeldaImportacionTrabajo(
  id: string | number,
  numeroFila: number,
  columna: string,
  valor: string,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPut<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/filas/${numeroFila}/celda`,
    { columna, valor },
    signal,
  )
}

export function deshacerCorreccionCeldaImportacionTrabajo(
  id: string | number,
  numeroFila: number,
  columna: string,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/filas/${numeroFila}/celda/deshacer`,
    { columna },
    signal,
  )
}

// ---- Deshacer en bloque (Fase 6.8C.4) ----

export function deshacerTodasLasCorreccionesDeFila(
  id: string | number,
  numeroFila: number,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/filas/${numeroFila}/correcciones/deshacer-todas`,
    undefined,
    signal,
  )
}

export function deshacerTodasLasCorrecciones(
  id: string | number,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/correcciones/deshacer-todas`,
    undefined,
    signal,
  )
}

export function deshacerTodasLasExclusiones(
  id: string | number,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/exclusiones/deshacer-todas`,
    undefined,
    signal,
  )
}

/** Vacía correcciones y exclusiones a la vez. No modifica el archivo original. */
export function restaurarOriginalImportacionTrabajo(
  id: string | number,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/restaurar-original`,
    undefined,
    signal,
  )
}

// ---- Corrección asistida (Fase 6.8C.4) ----

export interface RellenarColumnaParams {
  nombreColumna: string
  /** Si se indica, solo se rellenan filas cuyo error activo coincida con este tipo. */
  tipoError?: string
  valor: string
  /** Por defecto true: solo filas con un error activo en esta columna. */
  soloFilasConEsteProblema?: boolean
}

export function rellenarColumnaImportacionTrabajo(
  id: string | number,
  params: RellenarColumnaParams,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/correcciones/rellenar-columna`,
    {
      nombreColumna: params.nombreColumna,
      tipoError: params.tipoError ?? null,
      valor: params.valor,
      soloFilasConEsteProblema: params.soloFilasConEsteProblema ?? true,
    },
    signal,
  )
}

export type EstrategiaNormalizacion = 'FECHA' | 'BOOLEANO' | 'NUMERO' | 'TEXTO_TRIM'

export function normalizarColumnaImportacionTrabajo(
  id: string | number,
  nombreColumna: string,
  estrategia: EstrategiaNormalizacion,
  signal?: AbortSignal,
): Promise<RevalidarImportacionTrabajoResponseDto> {
  return apiPost<RevalidarImportacionTrabajoResponseDto>(
    `/importaciones-trabajo/${id}/correcciones/normalizar-columna`,
    { nombreColumna, estrategia },
    signal,
  )
}

export function importarDesdeTrabajo(
  id: string | number,
  signal?: AbortSignal,
): Promise<ImportarDesdeTrabajoResponseDto> {
  return apiPost<ImportarDesdeTrabajoResponseDto>(`/importaciones-trabajo/${id}/importar`, undefined, signal)
}

export function descartarImportacionTrabajo(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/importaciones-trabajo/${id}`, signal)
}
