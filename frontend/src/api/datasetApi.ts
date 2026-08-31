import { apiDelete, apiGet, apiPost, apiPut } from './apiClient'
import type {
  BaseEvaluableDashboardDto,
  CampoClinicoRequestDto,
  CampoClinicoResponseDto,
  DatasetClinicoRequestDto,
  DatasetClinicoResponseDto,
  DatasetFrontendMetadataResponseDto,
  ImportacionTrabajoResponseDto,
  PropuestaWidgetDto,
  ReanudarBorradorDatasetDto,
} from './types'

export function listarDatasets(
  incluirBorradores = false,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto[]> {
  return apiGet<DatasetClinicoResponseDto[]>(
    `/datasets-clinicos?incluirBorradores=${incluirBorradores}`,
    signal,
  )
}

export function obtenerDataset(
  id: string | number,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiGet<DatasetClinicoResponseDto>(`/datasets-clinicos/${id}`, signal)
}

export function crearDataset(
  payload: DatasetClinicoRequestDto,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiPost<DatasetClinicoResponseDto>('/datasets-clinicos', payload, signal)
}

export function actualizarDataset(
  id: string | number,
  payload: DatasetClinicoRequestDto,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiPut<DatasetClinicoResponseDto>(`/datasets-clinicos/${id}`, payload, signal)
}

export function eliminarDataset(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/datasets-clinicos/${id}`, signal)
}

/** Promueve un dataset BORRADOR/VALIDANDO a ACTIVO (importación completada con éxito). */
export function activarDataset(
  id: string | number,
  signal?: AbortSignal,
): Promise<DatasetClinicoResponseDto> {
  return apiPost<DatasetClinicoResponseDto>(`/datasets-clinicos/${id}/activar`, undefined, signal)
}

/** Borra un dataset BORRADOR/VALIDANDO y sus dependencias temporales. Rechaza si tiene registros reales. */
export function descartarDatasetBorrador(id: string | number, signal?: AbortSignal): Promise<void> {
  return apiDelete(`/datasets-clinicos/${id}/descartar-borrador`, signal)
}

/**
 * Retoma un dataset BORRADOR/VALIDANDO: dice si puede reanudarse, en qué paso y
 * con qué copia de trabajo.
 *
 * Es POST porque puede escribir —rescata una copia que quedara descartada sin
 * que el usuario lo pidiera—, y una consulta no debe cambiar nada. Repetirla
 * devuelve siempre lo mismo.
 */
export function reanudarDatasetBorrador(
  id: string | number,
  signal?: AbortSignal,
): Promise<ReanudarBorradorDatasetDto> {
  return apiPost<ReanudarBorradorDatasetDto>(`/datasets-clinicos/${id}/reanudar-borrador`, undefined, signal)
}

/**
 * Recupera la revisión anterior de un borrador, a petición del usuario.
 *
 * Reanudar solo avisa de que existe; resucitarla es esta llamada. La copia está
 * descartada sin constancia de si fue a propósito, y esa duda la resuelve el
 * usuario, no la aplicación.
 */
export function recuperarTrabajoAnterior(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<ImportacionTrabajoResponseDto> {
  return apiPost<ImportacionTrabajoResponseDto>(
    `/datasets-clinicos/${datasetId}/recuperar-trabajo-anterior`,
    undefined,
    signal,
  )
}

/**
 * Indicadores de infección quirúrgica que deben abrir el dashboard inicial de
 * este dataset. Lista vacía cuando el dataset no trata de eso, y entonces el
 * dashboard se genera solo con las reglas genéricas.
 */
export function obtenerBloqueInicialIlq(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<PropuestaWidgetDto[]> {
  return apiGet<PropuestaWidgetDto[]>(`/datasets-clinicos/${datasetId}/dashboard-inicial/bloque-ilq`, signal)
}

/**
 * Sobre qué población deben contar los indicadores de actividad del dashboard
 * inicial de este dataset.
 */
export function obtenerBaseEvaluableDashboard(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<BaseEvaluableDashboardDto> {
  return apiGet<BaseEvaluableDashboardDto>(
    `/datasets-clinicos/${datasetId}/dashboard-inicial/base-evaluable`,
    signal,
  )
}

export function listarCampos(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<CampoClinicoResponseDto[]> {
  return apiGet<CampoClinicoResponseDto[]>(`/datasets-clinicos/${datasetId}/campos`, signal)
}

/**
 * Deja listos los campos de una importación: reutiliza los que ya existan en el
 * dataset y crea solo los que falten.
 *
 * Es lo que usa el asistente. `crearCampo` rechaza códigos repetidos —y debe
 * seguir haciéndolo—, pero reanudar un borrador vuelve a declarar las mismas
 * columnas, y eso no es un error.
 */
export function asegurarCampos(
  datasetId: string | number,
  campos: CampoClinicoRequestDto[],
  signal?: AbortSignal,
): Promise<CampoClinicoResponseDto[]> {
  return apiPost<CampoClinicoResponseDto[]>(`/datasets-clinicos/${datasetId}/campos/asegurar`, campos, signal)
}

export function crearCampo(
  datasetId: string | number,
  payload: CampoClinicoRequestDto,
  signal?: AbortSignal,
): Promise<CampoClinicoResponseDto> {
  return apiPost<CampoClinicoResponseDto>(`/datasets-clinicos/${datasetId}/campos`, payload, signal)
}

export function actualizarCampo(
  datasetId: string | number,
  campoId: string | number,
  payload: CampoClinicoRequestDto,
  signal?: AbortSignal,
): Promise<CampoClinicoResponseDto> {
  return apiPut<CampoClinicoResponseDto>(`/datasets-clinicos/${datasetId}/campos/${campoId}`, payload, signal)
}

export function eliminarCampo(
  datasetId: string | number,
  campoId: string | number,
  signal?: AbortSignal,
): Promise<void> {
  return apiDelete(`/datasets-clinicos/${datasetId}/campos/${campoId}`, signal)
}

export function obtenerFrontendMetadata(
  datasetId: string | number,
  signal?: AbortSignal,
): Promise<DatasetFrontendMetadataResponseDto> {
  return apiGet<DatasetFrontendMetadataResponseDto>(`/datasets-clinicos/${datasetId}/frontend-metadata`, signal)
}

/** Valores distintos ya presentes en los registros del dataset para un campo (para poblar selectores de filtro). */
export function listarValoresUnicosDeCampo(
  datasetId: string | number,
  codigoCampo: string,
  signal?: AbortSignal,
): Promise<string[]> {
  return apiGet<string[]>(
    `/datasets-clinicos/${datasetId}/campos/${encodeURIComponent(codigoCampo)}/valores`,
    signal,
  )
}
