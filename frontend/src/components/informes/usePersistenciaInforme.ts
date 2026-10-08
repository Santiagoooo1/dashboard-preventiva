import { useCallback, useRef, useState } from 'react'

export type EstadoPersistencia = 'guardado' | 'guardando' | 'error'

interface Persistencia {
  estado: EstadoPersistencia
  mensajeError: string | null
  /** Encola una mutación. Las mutaciones se ejecutan de una en una, en orden. */
  mutar: (accion: () => Promise<unknown>, alTerminar?: () => Promise<void> | void) => Promise<boolean>
  /** Reintenta la última mutación que falló, si la hay. */
  reintentar: () => Promise<boolean>
  puedeReintentar: boolean
  /** Resuelve cuando la cola queda vacía. Sin esperas fijas. */
  esperarInactividad: () => Promise<void>
}

/**
 * Estado de guardado del documento, único para todo el editor (Fase 6.9S.0).
 *
 * <p>Antes había dos modelos mentales conviviendo: el título necesitaba pulsar
 * «Guardar» y todo lo demás —bloques, anchos, orden, páginas— se guardaba solo.
 * El resultado era que se podía mover un widget y seguir leyendo «✓ Guardado»,
 * que es verdad y a la vez no explica nada. Aquí todas las mutaciones pasan por
 * el mismo sitio y el documento tiene un solo estado.
 *
 * <p><b>Las mutaciones se serializan.</b> Es lo que evita que una petición
 * lenta pise a otra posterior: si el usuario teclea «Informe I», «Informe IL» e
 * «Informe ILQ», la cola las ejecuta en orden y la última en responder es la
 * última que se pidió. Con peticiones en paralelo, la primera podría contestar
 * al final y devolver el título a «Informe I».
 */
export function usePersistenciaInforme(): Persistencia {
  const [estado, setEstado] = useState<EstadoPersistencia>('guardado')
  const [mensajeError, setMensajeError] = useState<string | null>(null)
  const [puedeReintentar, setPuedeReintentar] = useState(false)

  // La cola es una cadena de promesas: cada mutación se engancha al final de la
  // anterior. No hace falta un array ni un lock.
  const cola = useRef<Promise<unknown>>(Promise.resolve())
  const pendientes = useRef(0)
  const ultimaFallida = useRef<{
    accion: () => Promise<unknown>
    alTerminar?: () => Promise<void> | void
  } | null>(null)

  const ejecutar = useCallback(
    async (
      accion: () => Promise<unknown>,
      alTerminar?: () => Promise<void> | void,
    ): Promise<boolean> => {
      pendientes.current += 1
      setEstado('guardando')

      const turno = cola.current.then(async () => {
        try {
          await accion()
          await alTerminar?.()
          return true
        } catch (e) {
          ultimaFallida.current = { accion, alTerminar }
          setMensajeError(e instanceof Error ? e.message : 'No se pudo guardar.')
          return false
        }
      })

      // La cola nunca se rompe: si una mutación falla, la siguiente sigue su
      // turno en vez de quedarse esperando para siempre.
      cola.current = turno.catch(() => undefined)

      const ok = await turno
      pendientes.current -= 1
      // Solo la última mutación en salir decide el estado final: con varias en
      // vuelo, la primera en terminar no puede anunciar «guardado».
      if (pendientes.current === 0) {
        if (ok && ultimaFallida.current === null) {
          setEstado('guardado')
          setMensajeError(null)
          setPuedeReintentar(false)
        } else {
          setEstado('error')
          setPuedeReintentar(true)
        }
      }
      return ok
    },
    [],
  )

  const reintentar = useCallback(async () => {
    const fallida = ultimaFallida.current
    if (!fallida) return true
    ultimaFallida.current = null
    setMensajeError(null)
    const ok = await ejecutar(fallida.accion, fallida.alTerminar)
    return ok
  }, [ejecutar])

  /**
   * Espera a que no quede nada por guardar.
   *
   * <p>La usan «Vista previa» y «Descargar PDF»: leen del servidor, así que
   * abrir cualquiera de las dos con una mutación a medio camino mostraría una
   * versión que no es la que el usuario está viendo. No es una espera fija, es
   * la propia cola la que avisa.
   */
  const esperarInactividad = useCallback(async () => {
    await cola.current.catch(() => undefined)
  }, [])

  return { estado, mensajeError, mutar: ejecutar, reintentar, puedeReintentar, esperarInactividad }
}
