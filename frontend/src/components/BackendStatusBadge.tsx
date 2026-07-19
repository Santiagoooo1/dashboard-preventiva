import { useEffect, useState } from 'react'
import { apiGetText } from '../api/apiClient'

type Estado = 'comprobando' | 'ok' | 'error'

export function BackendStatusBadge() {
  const [estado, setEstado] = useState<Estado>('comprobando')
  const [mensaje, setMensaje] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    apiGetText('/health', controller.signal)
      .then((texto) => {
        if (!controller.signal.aborted) {
          setEstado('ok')
          setMensaje(texto)
        }
      })
      .catch((err: unknown) => {
        if (!controller.signal.aborted) {
          setEstado('error')
          setMensaje(err instanceof Error ? err.message : 'Error de conexión.')
        }
      })

    return () => controller.abort()
  }, [])

  const etiqueta =
    estado === 'comprobando'
      ? 'Comprobando backend…'
      : estado === 'ok'
        ? mensaje
        : `Backend no disponible: ${mensaje}`

  return (
    <span data-estado={estado} role="status">
      {etiqueta}
    </span>
  )
}
