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

  const desactivar = async () => {
    if (!window.confirm(`¿Desactivar el panel '${panel.nombre}'?`)) {
      return
    }
    setOcupado(true)
    try {
      await desactivarPanel(panel.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al desactivar el panel.')
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
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={desactivar}>
        Desactivar
      </button>
    </div>
  )
}
