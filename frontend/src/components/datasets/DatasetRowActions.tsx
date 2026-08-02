import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import type { DatasetClinicoResponseDto } from '../../api/types'
import { eliminarDataset } from '../../api/datasetApi'
import { buscarDashboardInicialExistente } from '../../utils/dashboardInicial/orquestadorDashboardInicial'

interface DatasetRowActionsProps {
  dataset: DatasetClinicoResponseDto
  onError: (mensaje: string) => void
  onEliminado: () => void
}

export function DatasetRowActions({ dataset, onError, onEliminado }: DatasetRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)
  // Solo se comprueba para datasets activos: un borrador nunca tiene dashboard inicial todavía.
  const [panelDashboardId, setPanelDashboardId] = useState<number | null>(null)

  useEffect(() => {
    if (dataset.estadoDataset !== 'ACTIVO') return
    let cancelado = false
    buscarDashboardInicialExistente(dataset.id)
      .then((panel) => {
        if (!cancelado) setPanelDashboardId(panel?.id ?? null)
      })
      .catch(() => {
        // Si falla la comprobación, se deja el enlace "Dashboard" genérico (al detalle) en vez de bloquear la fila.
      })
    return () => {
      cancelado = true
    }
  }, [dataset.estadoDataset, dataset.id])

  const archivar = async () => {
    if (
      !window.confirm(
        `¿Archivar el dataset '${dataset.nombre}'? El dataset se ocultará de los listados principales, pero no se eliminará definitivamente de la base de datos.`,
      )
    ) {
      return
    }
    setOcupado(true)
    try {
      await eliminarDataset(dataset.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al archivar el dataset.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnAction" to={`/datasets/${dataset.id}`}>
        Detalle
      </Link>
      {dataset.estadoDataset === 'ACTIVO' &&
        (panelDashboardId !== null ? (
          <Link className="btn btnAction" to={`/paneles/${panelDashboardId}/dashboard?inicial=1`}>
            Ver dashboard
          </Link>
        ) : (
          <Link className="btn btnAction" to={`/datasets/${dataset.id}`}>
            Crear dashboard
          </Link>
        ))}
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/campos`}>
        Campos
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/metricas`}>
        Configurar métricas
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/paneles`}>
        Configurar paneles
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/editar`}>
        Editar datos básicos
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={archivar}>
        Archivar
      </button>
    </div>
  )
}
