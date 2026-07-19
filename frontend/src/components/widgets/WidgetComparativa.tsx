import type { ComparativaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'

interface WidgetComparativaProps {
  comparativa: ComparativaResponseDto
}

export function WidgetComparativa({ comparativa }: WidgetComparativaProps) {
  return (
    <DataTable
      columns={[
        { key: 'etiqueta', header: 'Etiqueta' },
        { key: 'valor', header: 'Valor' },
        { key: 'totalNumerador', header: 'Numerador', render: (i) => i.totalNumerador ?? '—' },
        { key: 'totalDenominador', header: 'Denominador', render: (i) => i.totalDenominador ?? '—' },
      ]}
      rows={comparativa.items}
      getRowKey={(item, index) => `${item.etiqueta}-${index}`}
    />
  )
}
