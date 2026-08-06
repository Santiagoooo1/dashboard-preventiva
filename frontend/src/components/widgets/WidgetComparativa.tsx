import type { ComparativaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import { formatearEtiquetaCategoria } from '../dashboard/camposFiltroDashboard'
import { filaInteractivaDeCategoria } from '../dashboard/tablaSeleccionable'
import type { SeleccionChart } from '../dashboard/BarChartWidget'

interface WidgetComparativaProps {
  comparativa: ComparativaResponseDto
  seleccion?: SeleccionChart
}

/**
 * Operaciones cuyo resultado ES una proporción, y solo ellas tienen numerador
 * y denominador con significado propio.
 *
 * En las demás el motor también los rellena (una media informa de cuántos
 * valores promedió), pero mostrarlos en columnas tituladas «Numerador» y
 * «Denominador» los presenta como los términos de una fracción que no existe:
 * la media de edad no es «250 partido por 280».
 */
const OPERACIONES_CON_PROPORCION = new Set(['PORCENTAJE', 'COMPLETITUD'])

export function WidgetComparativa({ comparativa, seleccion }: WidgetComparativaProps) {
  const esProporcion = OPERACIONES_CON_PROPORCION.has(comparativa.tipoMetrica)

  return (
    <DataTable
      filaInteractiva={filaInteractivaDeCategoria<ComparativaResponseDto['items'][number]>(seleccion)}
      columns={[
        // El valor técnico de `item.etiqueta` se conserva intacto en los datos;
        // aquí solo se formatea la presentación (true → Sí).
        { key: 'etiqueta', header: 'Etiqueta', render: (i) => formatearEtiquetaCategoria(i.etiqueta) },
        { key: 'valor', header: 'Valor' },
        ...(esProporcion
          ? [
              { key: 'totalNumerador', header: 'Numerador', render: (i: ComparativaResponseDto['items'][number]) => i.totalNumerador ?? '—' },
              { key: 'totalDenominador', header: 'Denominador', render: (i: ComparativaResponseDto['items'][number]) => i.totalDenominador ?? '—' },
            ]
          : []),
      ]}
      rows={comparativa.items}
      getRowKey={(item, index) => `${item.etiqueta}-${index}`}
    />
  )
}
