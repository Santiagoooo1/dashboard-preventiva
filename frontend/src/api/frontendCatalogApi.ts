import { apiGet } from './apiClient'
import type { CatalogoFrontendResponseDto } from './types'

export function getCatalogo(signal?: AbortSignal): Promise<CatalogoFrontendResponseDto> {
  return apiGet<CatalogoFrontendResponseDto>('/frontend/catalogo', signal)
}
