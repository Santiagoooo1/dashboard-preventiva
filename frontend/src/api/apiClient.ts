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

// fetch() rechaza con un TypeError de bajo nivel ("Failed to fetch",
// "NetworkError...") cuando el backend no responde en absoluto (caído, CORS,
// sin red). Se traduce a un mensaje claro; un AbortError sigue propagándose
// tal cual porque los llamantes ya lo distinguen vía signal.aborted.
async function fetchOFallarConMensajeClaro(input: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(input, init)
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    throw new Error('No se pudo conectar con el backend.')
  }
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetchOFallarConMensajeClaro(`${BASE_URL}${path}`, { signal })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
  return (await response.json()) as T
}

export async function apiPost<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetchOFallarConMensajeClaro(`${BASE_URL}${path}`, {
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

// Envío multipart: NO se fija Content-Type a propósito. El navegador debe
// generarlo junto con el boundary; fijarlo a mano rompe el parseo en el backend.
export async function apiPostFormData<T>(
  path: string,
  formData: FormData,
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetchOFallarConMensajeClaro(`${BASE_URL}${path}`, {
    method: 'POST',
    body: formData,
    signal,
  })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
  return (await response.json()) as T
}

export async function apiPut<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetchOFallarConMensajeClaro(`${BASE_URL}${path}`, {
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
  const response = await fetchOFallarConMensajeClaro(`${BASE_URL}${path}`, { method: 'DELETE', signal })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
}

export async function apiGetText(path: string, signal?: AbortSignal): Promise<string> {
  const response = await fetchOFallarConMensajeClaro(`${BASE_URL}${path}`, { signal })
  if (!response.ok) {
    throw new Error(await leerMensajeError(response))
  }
  return response.text()
}
