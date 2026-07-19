import type { ApiErrorResponseDto } from './types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL

async function leerMensajeError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as ApiErrorResponseDto
    if (body?.message) {
      if (body.fieldErrors && body.fieldErrors.length > 0) {
        const detalle = body.fieldErrors.map((fe) => `${fe.campo}: ${fe.mensaje}`).join('; ')
        return `${body.message} (${detalle})`
      }
      return body.message
    }
  } catch {
    // el cuerpo no era JSON; se usa el mensaje de respaldo de abajo
  }
  return `Error ${response.status}: ${response.statusText}`
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, { signal })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
  return (await response.json()) as T
}

export async function apiPost<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
    signal,
  })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
  return (await response.json()) as T
}

export async function apiPut<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
    signal,
  })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
  return (await response.json()) as T
}

export async function apiDelete(path: string, signal?: AbortSignal): Promise<void> {
  const response = await fetch(`${BASE_URL}${path}`, { method: 'DELETE', signal })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
}

export async function apiGetText(path: string, signal?: AbortSignal): Promise<string> {
  const response = await fetch(`${BASE_URL}${path}`, { signal })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
  return response.text()
}
