import { useEffect, useState } from 'react'
import type { TrazabilidadImportacionTrabajoResponseDto } from '../../api/types'
import {
  obtenerTrazabilidadImportacionTrabajo,
  obtenerTrazabilidadPorImportacionGenerica,
} from '../../api/trazabilidadImportacionApi'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import { ResumenTrazabilidadCard } from './ResumenTrazabilidadCard'
import { TimelineEventosImportacion } from './TimelineEventosImportacion'

interface TrazabilidadImportacionPanelProps {
  /** Preferido si ambos están disponibles: cubre correcciones hechas antes de importar. */
  importacionTrabajoId?: number
  /** Alternativa cuando ya no se dispone del id de la copia de trabajo. */
  importacionGenericaId?: number
  titulo?: string
}

const MENSAJE_NO_ENCONTRADA =
  'No se encontró trazabilidad para esta importación. Puede que se haya realizado sin copia interna.'
const MENSAJE_FALLO_CARGA = 'No se pudo cargar la trazabilidad.'

export function TrazabilidadImportacionPanel({
  importacionTrabajoId,
  importacionGenericaId,
  titulo,
}: TrazabilidadImportacionPanelProps) {
  const [datos, setDatos] = useState<TrazabilidadImportacionTrabajoResponseDto | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelado = false

    async function cargar() {
      setCargando(true)
      setError(null)
      setDatos(null)
      try {
        let resultado: TrazabilidadImportacionTrabajoResponseDto
        if (importacionTrabajoId !== undefined) {
          resultado = await obtenerTrazabilidadImportacionTrabajo(importacionTrabajoId)
        } else if (importacionGenericaId !== undefined) {
          resultado = await obtenerTrazabilidadPorImportacionGenerica(importacionGenericaId)
        } else {
          throw new Error(MENSAJE_NO_ENCONTRADA)
        }
        if (!cancelado) setDatos(resultado)
      } catch (err) {
        if (cancelado) return
        const mensaje = err instanceof Error ? err.message : ''
        // Un 404 llega como Error("Error 404: Not Found") o con el mensaje real
        // del backend (verificado contra el backend real: "No existe la copia
        // de trabajo con id: X" o "No hay copia de trabajo asociada a la
        // importación genérica X"); en cualquier caso se prefiere el texto
        // amable fijado por el punto 11 del spec antes que el mensaje técnico.
        const pareceNoEncontrada = /404|no existe|no hay|no encontr/i.test(mensaje)
        setError(pareceNoEncontrada ? MENSAJE_NO_ENCONTRADA : mensaje || MENSAJE_FALLO_CARGA)
      } finally {
        if (!cancelado) setCargando(false)
      }
    }

    cargar()
    return () => {
      cancelado = true
    }
  }, [importacionTrabajoId, importacionGenericaId])

  return (
    <Card title={titulo ?? 'Trazabilidad de la importación'}>
      {cargando && (
        <p className="stateLoading" role="status">
          Cargando…
        </p>
      )}
      <ErrorBanner mensaje={cargando ? null : error} />
      {!cargando && !error && datos && (
        <>
          <ResumenTrazabilidadCard resumen={datos.resumen} />
          <TimelineEventosImportacion eventos={datos.eventos} />
        </>
      )}
    </Card>
  )
}
