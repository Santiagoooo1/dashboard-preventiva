import { useParams } from 'react-router'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { TrazabilidadImportacionPanel } from '../components/trazabilidad/TrazabilidadImportacionPanel'

export function TrazabilidadImportacionGenericaPage() {
  const { id } = useParams<{ id: string }>()
  const importacionGenericaId = Number(id)

  return (
    <div>
      <Breadcrumbs items={[{ label: 'Trazabilidad de la importación' }]} />
      {Number.isFinite(importacionGenericaId) ? (
        <TrazabilidadImportacionPanel
          importacionGenericaId={importacionGenericaId}
          titulo="Trazabilidad de la importación"
        />
      ) : (
        <p className="stateError">El identificador de la importación no es válido.</p>
      )}
    </div>
  )
}
