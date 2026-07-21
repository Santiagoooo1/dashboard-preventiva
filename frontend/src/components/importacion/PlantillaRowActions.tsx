import { useState } from 'react'
import { Link } from 'react-router'
import type { PlantillaImportacionResponseDto } from '../../api/types'
import { eliminarPlantillaImportacion } from '../../api/plantillasImportacionApi'

interface PlantillaRowActionsProps {
  datasetId: string | number
  plantilla: PlantillaImportacionResponseDto
  onError: (mensaje: string) => void
  onEliminada: () => void
}

export function PlantillaRowActions({
  datasetId,
  plantilla,
  onError,
  onEliminada,
}: PlantillaRowActionsProps) {
  const [ocupado, setOcupado] = useState(false)

  const archivar = async () => {
    if (
      !window.confirm(
        `¿Archivar la plantilla '${plantilla.nombre}'? Se ocultará de los listados principales, pero no se eliminará definitivamente de la base de datos.`,
      )
    ) {
      return
    }
    setOcupado(true)
    try {
      await eliminarPlantillaImportacion(plantilla.id)
      onEliminada()
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Error al archivar la plantilla.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="rowActions">
      <Link className="btn btnAction" to={`/datasets/${datasetId}/plantillas/${plantilla.id}/mapeos`}>
        Mapeos
      </Link>
      <Link className="btn btnAction" to={`/datasets/${datasetId}/plantillas/${plantilla.id}/editar`}>
        Editar
      </Link>
      <button type="button" className="btn btnDanger" disabled={ocupado} onClick={archivar}>
        Archivar
      </button>
    </div>
  )
}
