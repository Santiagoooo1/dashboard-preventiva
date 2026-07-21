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

  const archivar = async () => {
    if (
      !window.confirm(
        `¿Archivar el mapeo de la columna '${mapeo.nombreColumnaOrigen}'? Se ocultará de los listados principales, pero no se eliminará definitivamente de la base de datos.`,
      )
    ) {
      return
    }
    setOcupado(true)
    try {
      await eliminarMapeoPlantilla(plantillaId, mapeo.id)
      onEliminado()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al archivar el mapeo.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <button type="button" className="btn btnAction" onClick={onEditar}>
        Editar
      </button>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={archivar}>
        Archivar
      </button>
    </div>
  )
}
