import { useParams } from 'react-router'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { TrazabilidadImportacionPanel } from '../components/trazabilidad/TrazabilidadImportacionPanel'

export function TrazabilidadImportacionTrabajoPage() {
  const { id } = useParams<{ id: string }>()
  const importacionTrabajoId = Number(id)

  return (
    <div>
      <Breadcrumbs items={[{ label: 'Trazabilidad de la copia interna' }]} />
      {Number.isFinite(importacionTrabajoId) ? (
        <TrazabilidadImportacionPanel
          importacionTrabajoId={importacionTrabajoId}
          titulo="Trazabilidad de la copia interna"
        />
      ) : (
        <p className="stateError">El identificador de la copia interna no es válido.</p>
      )}
    </div>
  )
}
