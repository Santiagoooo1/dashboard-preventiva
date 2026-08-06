import { useState } from 'react'
import type { PanelMetricaResponseDto } from '../../api/types'
import { quitarWidget } from '../../api/panelesApi'

interface WidgetRowActionsProps {
  panelId: string | number
  widget: PanelMetricaResponseDto
  /** La métrica admite comparativa o serie temporal (lo dice el backend). */
  admiteAgrupar: boolean
  onEditar: () => void
  onAgrupar: () => void
  onError: (mensaje: string) => void
  onQuitado: () => void
}

export function WidgetRowActions({
  panelId,
  widget,
  admiteAgrupar,
  onEditar,
  onAgrupar,
  onError,
  onQuitado,
}: WidgetRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const quitar = async () => {
    const nombre = widget.tituloPersonalizado ?? widget.metricaNombre
    if (!window.confirm(`¿Quitar «${nombre}» de este dashboard? El indicador seguirá en el catálogo.`)) {
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
        Editar visualización
      </button>
      {/* Deshabilitado con explicación, nunca activo sin efecto: una
          distribución o una categoría más frecuente no se pueden agrupar, y
          hasta ahora el botón abría un formulario con una sola opción. */}
      <button
        type="button"
        className="btn btnAction"
        onClick={onAgrupar}
        disabled={!admiteAgrupar}
        title={
          admiteAgrupar
            ? undefined
            : 'Esta métrica no admite agrupación: su resultado es un reparto de categorías o una etiqueta.'
        }
      >
        Agrupar o segmentar
      </button>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={quitar}>
        Quitar del dashboard
      </button>
    </div>
  )
}
