import { Link } from 'react-router'
import { Card } from '../Card'

interface ReanudacionBorradorInfoProps {
  mensaje: string
  datasetId: number | null
  onCrearNuevo: () => void
}

// Pantalla mostrada al entrar a /crear-dashboard/borrador/:datasetId cuando el
// borrador no puede reanudarse en el asistente (ya está activo, se descartó,
// o su paso recomendado no encaja en el flujo guiado, p. ej. DETALLE_DATASET).
export function ReanudacionBorradorInfo({ mensaje, datasetId, onCrearNuevo }: ReanudacionBorradorInfoProps) {
  return (
    <Card title="No se puede continuar este borrador">
      <p>{mensaje}</p>
      <div className="rowActions">
        {datasetId !== null && (
          <Link className="btn btnPrimary" to={`/datasets/${datasetId}`}>
            Ir al detalle del dataset
          </Link>
        )}
        <button type="button" className="btn btnSecondary" onClick={onCrearNuevo}>
          Crear nuevo dashboard
        </button>
      </div>
    </Card>
  )
}
