import type { ComparativaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import { formatearEtiquetaCategoria } from '../dashboard/camposFiltroDashboard'
import { filaInteractivaDeCategoria } from '../dashboard/tablaSeleccionable'
import type { SeleccionChart } from '../dashboard/BarChartWidget'

interface WidgetComparativaProps {
  comparativa: ComparativaResponseDto
  seleccion?: SeleccionChart
}

export function WidgetComparativa({ comparativa, seleccion }: WidgetComparativaProps) {
  return (
    <DataTable
      filaInteractiva={filaInteractivaDeCategoria<ComparativaResponseDto['items'][number]>(seleccion)}
      columns={[
        // El valor técnico de `item.etiqueta` se conserva intacto en los datos;
        // aquí solo se formatea la presentación (true → Sí).
        { key: 'etiqueta', header: 'Etiqueta', render: (i) => formatearEtiquetaCategoria(i.etiqueta) },
        { key: 'valor', header: 'Valor' },
        { key: 'totalNumerador', header: 'Numerador', render: (i) => i.totalNumerador ?? '—' },
        { key: 'totalDenominador', header: 'Denominador', render: (i) => i.totalDenominador ?? '—' },
      ]}
      rows={comparativa.items}
      getRowKey={(item, index) => `${item.etiqueta}-${index}`}
    />
  )
}
