import { apiGet, apiPostFormData } from './apiClient'
import type {
  ErrorImportacionGenericaResponseDto,
  ImportacionGenericaResponseDto,
  ValidacionFilasImportacionGenericaResponseDto,
  ValidacionImportacionGenericaResponseDto,
} from './types'

export interface ImportacionPayload {
  archivo: File
  plantillaId: number
  indiceHoja?: number
  filaCabecera?: number
}

/**
 * El backend decide si un archivo es CSV en WorkbookLoader.esCsv(), que mira el
 * nombre (termina en .csv) O el content-type. Entre los content-type que trata
 * como CSV está "application/vnd.ms-excel", que es justo el que envían los
 * navegadores para los .xls: un .xls real acabaría parseándose como texto plano
 * y sus cabeceras saldrían como bytes binarios.
 *
 * Enviando el archivo con un tipo neutro, esa rama por content-type no se activa
 * y el backend decide por extensión: los .csv se siguen detectando por el nombre
 * y los .xls/.xlsx llegan a POI, que sí sabe leerlos.
 */
function archivoConTipoNeutro(archivo: File): File {
  return new File([archivo], archivo.name, { type: 'application/octet-stream' })
}

function construirFormData(payload: ImportacionPayload): FormData {
  const formData = new FormData()
  formData.append('archivo', archivoConTipoNeutro(payload.archivo), payload.archivo.name)
  formData.append('plantillaId', String(payload.plantillaId))
  formData.append('indiceHoja', String(payload.indiceHoja ?? 0))
  if (payload.filaCabecera !== undefined && payload.filaCabecera !== null) {
    formData.append('filaCabecera', String(payload.filaCabecera))
  }
  return formData
}

export function validarImportacionGenerica(
  payload: ImportacionPayload,
  signal?: AbortSignal,
): Promise<ValidacionImportacionGenericaResponseDto> {
  return apiPostFormData<ValidacionImportacionGenericaResponseDto>(
    '/importaciones-genericas/validar',
    construirFormData(payload),
    signal,
  )
}

export function validarFilasImportacionGenerica(
  payload: ImportacionPayload,
  signal?: AbortSignal,
): Promise<ValidacionFilasImportacionGenericaResponseDto> {
  return apiPostFormData<ValidacionFilasImportacionGenericaResponseDto>(
    '/importaciones-genericas/validar-filas',
    construirFormData(payload),
    signal,
  )
}

export function importarGenerico(
  payload: ImportacionPayload,
  signal?: AbortSignal,
): Promise<ImportacionGenericaResponseDto> {
  return apiPostFormData<ImportacionGenericaResponseDto>(
    '/importaciones-genericas/importar',
    construirFormData(payload),
    signal,
  )
}

export function obtenerImportacionGenerica(
  id: string | number,
  signal?: AbortSignal,
): Promise<ImportacionGenericaResponseDto> {
  return apiGet<ImportacionGenericaResponseDto>(`/importaciones-genericas/${id}`, signal)
}

export function obtenerErroresImportacionGenerica(
  id: string | number,
  signal?: AbortSignal,
): Promise<ErrorImportacionGenericaResponseDto[]> {
  return apiGet<ErrorImportacionGenericaResponseDto[]>(`/importaciones-genericas/${id}/errores`, signal)
}
