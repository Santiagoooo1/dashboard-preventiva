import { useState } from 'react'
import { Link } from 'react-router'
import type { MetricaClinicaResponseDto, ResultadoMetricaResponseDto } from '../../api/types'
import { desactivarMetrica, ejecutarMetrica } from '../../api/metricasApi'

interface MetricaRowActionsProps {
  metrica: MetricaClinicaResponseDto
  onResultado: (metrica: MetricaClinicaResponseDto, resultado: ResultadoMetricaResponseDto) => void
  onError: (mensaje: string) => void
  onDesactivada: () => void
}

export function MetricaRowActions({ metrica, onResultado, onError, onDesactivada }: MetricaRowActionsProps) {
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

  const desactivar = async () => {
    if (!window.confirm(`¿Desactivar la métrica '${metrica.nombre}'?`)) {
      return
    }
    setOcupado(true)
    try {
      await desactivarMetrica(metrica.id)
      onDesactivada()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al desactivar la métrica.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnAction" to={`/datasets/${metrica.datasetId}/metricas/${metrica.id}/editar`}>
        Editar
      </Link>
      <button type="button" className="btn btnAction" disabled={ocupado} onClick={ejecutar}>
        Ejecutar
      </button>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={desactivar}>
        Desactivar
      </button>
    </div>
  )
}
