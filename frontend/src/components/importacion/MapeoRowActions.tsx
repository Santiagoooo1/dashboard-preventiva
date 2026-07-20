import { useState } from 'react'
import type { MapeoCampoImportacionResponseDto } from '../../api/types'
import { eliminarMapeoPlantilla } from '../../api/plantillasImportacionApi'

interface MapeoRowActionsProps {
  plantillaId: string | number
  mapeo: MapeoCampoImportacionResponseDto
  onEditar: () => void
  onError: (mensaje: string) => void
  onEliminado: () => void
}

export function MapeoRowActions({
  plantillaId,
  mapeo,
  onEditar,
  onError,
  onEliminado,
}: MapeoRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const desactivar = async () => {
    if (!window.confirm(`¿Desactivar el mapeo de la columna '${mapeo.nombreColumnaOrigen}'?`)) {
      return
    }
    setOcupado(true)
    try {
      await eliminarMapeoPlantilla(plantillaId, mapeo.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al desactivar el mapeo.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <button type="button" className="btn btnAction" onClick={onEditar}>
        Editar
      </button>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={desactivar}>
        Desactivar
      </button>
    </div>
  )
}
