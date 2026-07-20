import { useState } from 'react'
import type { PanelMetricaResponseDto } from '../../api/types'
import { quitarWidget } from '../../api/panelesApi'

interface WidgetRowActionsProps {
  panelId: string | number
  widget: PanelMetricaResponseDto
  onEditar: () => void
  onConfigurar: () => void
  onError: (mensaje: string) => void
  onQuitado: () => void
}

export function WidgetRowActions({
  panelId,
  widget,
  onEditar,
  onConfigurar,
  onError,
  onQuitado,
}: WidgetRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const quitar = async () => {
    const nombre = widget.tituloPersonalizado ?? widget.metricaNombre
    if (!window.confirm(`¿Quitar el widget '${nombre}' del panel?`)) {
      return
    }
    setOcupado(true)
    try {
      await quitarWidget(panelId, widget.id)
      onQuitado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al quitar el widget.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <button type="button" className="btn btnAction" onClick={onEditar}>
        Editar widget
      </button>
      <button type="button" className="btn btnAction" onClick={onConfigurar}>
        Configurar resultado
      </button>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={quitar}>
        Quitar
      </button>
    </div>
  )
}
