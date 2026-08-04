import type { ComparativaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import { formatearEtiquetaCategoria } from '../dashboard/camposFiltroDashboard'

interface WidgetComparativaProps {
  comparativa: ComparativaResponseDto
}

export function WidgetComparativa({ comparativa }: WidgetComparativaProps) {
  return (
    <DataTable
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
