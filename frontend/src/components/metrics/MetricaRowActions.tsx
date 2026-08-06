import { useState } from 'react'
import { Link } from 'react-router'
import type { MetricaClinicaResponseDto, ResultadoMetricaResponseDto } from '../../api/types'
import { desactivarMetrica, ejecutarMetrica } from '../../api/metricasApi'

interface MetricaRowActionsProps {
  metrica: MetricaClinicaResponseDto
  onResultado: (metrica: MetricaClinicaResponseDto, resultado: ResultadoMetricaResponseDto) => void
  onError: (mensaje: string) => void
  onDesactivada: () => void
  /** Abre el formulario rápido de widget para esta métrica (Fase 6.9I.4). */
  onAnadirADashboard: (metrica: MetricaClinicaResponseDto) => void
}

export function MetricaRowActions({
  metrica,
  onResultado,
  onError,
  onDesactivada,
  onAnadirADashboard,
}: MetricaRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const ejecutar = async () => {
    setOcupado(true)
    try {
      const resultado = await ejecutarMetrica(metrica.id)
      onResultado(metrica, resultado)
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al ejecutar la métrica.')
    } finally {
      setOcupado(false)
    }
  }

  const archivar = async () => {
    if (
      !window.confirm(
        `¿Archivar la métrica '${metrica.nombre}'? Se ocultará de los listados principales, pero no se eliminará definitivamente de la base de datos.`,
      )
    ) {
      return
    }
    setOcupado(true)
    try {
      await desactivarMetrica(metrica.id)
      onDesactivada()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al archivar la métrica.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      {/* Acción principal de la fila: una métrica del catálogo que nadie ve en
          ningún dashboard no sirve de nada, y hasta ahora llegar hasta ahí
          exigía ir al panel, abrir "Añadir widget" y buscarla en un
          desplegable. */}
      <button type="button" className="btn btnAction" onClick={() => onAnadirADashboard(metrica)}>
        Añadir al dashboard
      </button>
      <button type="button" className="btn btnAction" disabled={ocupado} onClick={ejecutar}>
        Ver resultado
      </button>
      <Link className="btn btnAction" to={`/datasets/${metrica.datasetId}/metricas/${metrica.id}/editar`}>
        Editar
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={archivar}>
        Archivar
      </button>
    </div>
  )
}
