import { apiDelete, apiGet, apiPost, apiPostFormData, apiPut } from './apiClient'
import type {
  DeteccionColumnasResponseDto,
  MapeoCampoImportacionRequestDto,
  MapeoCampoImportacionResponseDto,
  PlantillaImportacionRequestDto,
  PlantillaImportacionResponseDto,
} from './types'

export interface DeteccionColumnasPayload {
  archivo: File
  indiceHoja?: number
  filaCabecera?: number
}

export function listarPlantillasImportacion(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<PlantillaImportacionResponseDto[]> {
  return apiGet<PlantillaImportacionResponseDto[]>(
    `/datasets-clinicos/${datasetId}/plantillas-importacion`,
    signal,
  )
}

export function obtenerPlantillaImportacion(
  id: string | number,
  signal?: AbortSignal,
): Promise<PlantillaImportacionResponseDto> {
  return apiGet<PlantillaImportacionResponseDto>(`/plantillas-importacion/${id}`, signal)
}

export function crearPlantillaImportacion(
  datasetId: string | number,
  payload: PlantillaImportacionRequestDto,
  signal?: AbortSignal,
): Promise<PlantillaImportacionResponseDto> {
  return apiPost<PlantillaImportacionResponseDto>(
    `/datasets-clinicos/${datasetId}/plantillas-importacion`,
    payload,
    signal,
  )
}

export function actualizarPlantillaImportacion(
  id: string | number,
  payload: PlantillaImportacionRequestDto,
  signal?: AbortSignal,
): Promise<PlantillaImportacionResponseDto> {
  return apiPut<PlantillaImportacionResponseDto>(`/plantillas-importacion/${id}`, payload, signal)
}

export function eliminarPlantillaImportacion(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/plantillas-importacion/${id}`, signal)
}

// Mismo criterio de content-type neutro que en importacionesApi (ver comentario allí).
export function detectarColumnasPlantilla(
  payload: DeteccionColumnasPayload,
  signal?: AbortSignal,
): Promise<DeteccionColumnasResponseDto> {
  const formData = new FormData()
  const neutro = new File([payload.archivo], payload.archivo.name, { type: 'application/octet-stream' })
  formData.append('archivo', neutro, payload.archivo.name)
  formData.append('indiceHoja', String(payload.indiceHoja ?? 0))
  formData.append('filaCabecera', String(payload.filaCabecera ?? 0))
  return apiPostFormData<DeteccionColumnasResponseDto>(
    '/plantillas-importacion/detectar-columnas',
    formData,
    signal,
  )
}

export function listarMapeosPlantilla(
  plantillaId: string | number,
  signal?: AbortSignal,
): Promise<MapeoCampoImportacionResponseDto[]> {
  return apiGet<MapeoCampoImportacionResponseDto[]>(`/plantillas-importacion/${plantillaId}/mapeos`, signal)
}

export function crearMapeoPlantilla(
  plantillaId: string | number,
  payload: MapeoCampoImportacionRequestDto,
  signal?: AbortSignal,
): Promise<MapeoCampoImportacionResponseDto> {
  return apiPost<MapeoCampoImportacionResponseDto>(
    `/plantillas-importacion/${plantillaId}/mapeos`,
    payload,
    signal,
  )
}

export function actualizarMapeoPlantilla(
  plantillaId: string | number,
  mapeoId: string | number,
  payload: MapeoCampoImportacionRequestDto,
  signal?: AbortSignal,
): Promise<MapeoCampoImportacionResponseDto> {
  return apiPut<MapeoCampoImportacionResponseDto>(
    `/plantillas-importacion/${plantillaId}/mapeos/${mapeoId}`,
    payload,
    signal,
  )
}

export function eliminarMapeoPlantilla(
  plantillaId: string | number,
  mapeoId: string | number,
  signal?: AbortSignal,
): Promise<void> {
  return apiDelete(`/plantillas-importacion/${plantillaId}/mapeos/${mapeoId}`, signal)
}
