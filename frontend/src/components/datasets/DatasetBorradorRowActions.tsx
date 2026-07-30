import { useState } from 'react'
import { Link } from 'react-router'
import type { DatasetClinicoResponseDto } from '../../api/types'
import { descartarDatasetBorrador } from '../../api/datasetApi'

interface DatasetBorradorRowActionsProps {
  dataset: DatasetClinicoResponseDto
  onError: (mensaje: string) => void
  onDescartado: () => void
}

export function DatasetBorradorRowActions({ dataset, onError, onDescartado }: DatasetBorradorRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const descartar = async () => {
    if (
      !window.confirm(
        `¿Descartar la prueba '${dataset.nombre}'? Se eliminará este borrador y sus copias internas. El archivo original no se modificará.`,
      )
    ) {
      return
    }
    setOcupado(true)
    try {
      await descartarDatasetBorrador(dataset.id)
      onDescartado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al descartar la prueba.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnPrimary" to={`/crear-dashboard/borrador/${dataset.id}`}>
        Continuar creación
      </Link>
      <Link className="btn btnAction" to={`/datasets/${dataset.id}`}>
        Detalle
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={descartar}>
        Descartar prueba
      </button>
    </div>
  )
}
