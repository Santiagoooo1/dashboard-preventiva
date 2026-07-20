import { useState } from 'react'
import { Link } from 'react-router'
import type { DatasetClinicoResponseDto } from '../../api/types'
import { eliminarDataset } from '../../api/datasetApi'

interface DatasetRowActionsProps {
  dataset: DatasetClinicoResponseDto
  onError: (mensaje: string) => void
  onEliminado: () => void
}

export function DatasetRowActions({ dataset, onError, onEliminado }: DatasetRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const desactivar = async () => {
    if (!window.confirm(`¿Desactivar el dataset '${dataset.nombre}'?`)) {
      return
    }
    setOcupado(true)
    try {
      await eliminarDataset(dataset.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al desactivar el dataset.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnAction" to={`/datasets/${dataset.id}`}>
        Detalle
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/campos`}>
        Campos
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/metricas`}>
        Métricas
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/paneles`}>
        Paneles
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}/editar`}>
        Editar
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={desactivar}>
        Desactivar
      </button>
    </div>
  )
}
