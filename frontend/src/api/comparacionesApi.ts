import { apiPost } from './apiClient'
import type {
  ComparacionInteranualRequestDto,
  ComparacionInteranualResponseDto,
} from './types'

/**
 * Compara un concepto clínico entre varios años.
 *
 * Es POST porque la petición lleva cuerpo —datasets, concepto, filtros—, no
 * porque escriba: no persiste nada.
 */
export function compararInteranual(
  peticion: ComparacionInteranualRequestDto,
  signal?: AbortSignal,
): Promise<ComparacionInteranualResponseDto> {
  return apiPost<ComparacionInteranualResponseDto>('/comparaciones/interanual', peticion, signal)
}
