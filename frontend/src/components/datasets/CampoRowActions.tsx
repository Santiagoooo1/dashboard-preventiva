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

  const archivar = async () => {
    if (
      !window.confirm(
        `¿Archivar el campo '${campo.etiqueta}'? Se ocultará de los listados principales, pero no se eliminará definitivamente de la base de datos.`,
      )
    ) {
      return
    }
    setOcupado(true)
    try {
      await eliminarCampo(datasetId, campo.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al archivar el campo.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnAction" to={`/datasets/${datasetId}/campos/${campo.id}/editar`}>
        Editar
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={archivar}>
        Archivar
      </button>
    </div>
  )
}
