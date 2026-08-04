import type { ResultadoMetricaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import { formatNumber } from '../../utils/formatters'
import { formatearEtiquetaCategoria } from '../dashboard/camposFiltroDashboard'
import { filaInteractivaDeCategoria } from '../dashboard/tablaSeleccionable'
import type { SeleccionChart } from '../dashboard/BarChartWidget'

interface WidgetActualProps {
  resultado: ResultadoMetricaResponseDto
  seleccion?: SeleccionChart
}

export function WidgetActual({ resultado, seleccion }: WidgetActualProps) {
  if (resultado.items) {
    return (
      <DataTable
        filaInteractiva={filaInteractivaDeCategoria<NonNullable<ResultadoMetricaResponseDto['items']>[number]>(seleccion)}
        columns={[
          { key: 'etiqueta', header: 'Etiqueta', render: (i) => formatearEtiquetaCategoria(i.etiqueta) },
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
