import { useState } from 'react'
import { Link } from 'react-router'
import type { PanelClinicoResponseDto } from '../../api/types'
import { desactivarPanel } from '../../api/panelesApi'

interface PanelRowActionsProps {
  datasetId: string | number
  panel: PanelClinicoResponseDto
  onError: (mensaje: string) => void
  onEliminado: () => void
}

export function PanelRowActions({ datasetId, panel, onError, onEliminado }: PanelRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const archivar = async () => {
    if (
      !window.confirm(
        `¿Archivar el panel '${panel.nombre}'? Se ocultará de los listados principales, pero no se eliminará definitivamente de la base de datos.`,
      )
    ) {
      return
    }
    setOcupado(true)
    try {
      await desactivarPanel(panel.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al archivar el panel.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnAction" to={`/paneles/${panel.id}/dashboard`}>
        Ver dashboard
      </Link>
      <Link className="btn btnAction" to={`/datasets/${datasetId}/paneles/${panel.id}/widgets`}>
        Widgets
      </Link>
      <Link className="btn btnAction" to={`/datasets/${datasetId}/paneles/${panel.id}/editar`}>
        Editar
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={archivar}>
        Archivar
      </button>
    </div>
  )
}
