import type { ResultadoMetricaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import { formatNumber } from '../../utils/formatters'

interface WidgetActualProps {
  resultado: ResultadoMetricaResponseDto
}

export function WidgetActual({ resultado }: WidgetActualProps) {
  if (resultado.items) {
    return (
      <DataTable
        columns={[
          { key: 'etiqueta', header: 'Etiqueta' },
          { key: 'valor', header: 'Valor' },
        ]}
        rows={resultado.items}
        getRowKey={(item, index) => `${item.etiqueta}-${index}`}
      />
    )
  }

  const tieneTotales = resultado.totalNumerador !== null && resultado.totalNumerador !== undefined

  return (
    <div>
      <p className="bigValue">
        {formatNumber(resultado.valor)}
        {resultado.unidad ? ` ${resultado.unidad}` : ''}
      </p>
      {tieneTotales && (
        <p className="subValue">
          {resultado.totalNumerador} / {resultado.totalDenominador}
        </p>
      )}
    </div>
  )
}
