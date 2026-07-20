import { useState } from 'react'
import { Link } from 'react-router'
import type { CampoClinicoResponseDto } from '../../api/types'
import { eliminarCampo } from '../../api/datasetApi'

interface CampoRowActionsProps {
  datasetId: string | number
  campo: CampoClinicoResponseDto
  onError: (mensaje: string) => void
  onEliminado: () => void
}

export function CampoRowActions({ datasetId, campo, onError, onEliminado }: CampoRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const desactivar = async () => {
    if (!window.confirm(`¿Desactivar el campo '${campo.etiqueta}'?`)) {
      return
    }
    setOcupado(true)
    try {
      await eliminarCampo(datasetId, campo.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al desactivar el campo.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnAction" to={`/datasets/${datasetId}/campos/${campo.id}/editar`}>
        Editar
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={desactivar}>
        Desactivar
      </button>
    </div>
  )
}
