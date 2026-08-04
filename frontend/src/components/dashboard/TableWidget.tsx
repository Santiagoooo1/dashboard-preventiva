import type { DashboardWidgetDto } from '../../api/types'
import { WidgetActual } from '../widgets/WidgetActual'
import { WidgetComparativa } from '../widgets/WidgetComparativa'
import { WidgetSerieTemporal } from '../widgets/WidgetSerieTemporal'
import { ChartEmptyState, NotaVisualizacion } from './ChartEmptyState'
import type { MotivoVacio } from './ChartEmptyState'
import type { SeleccionChart } from './BarChartWidget'

interface TableWidgetProps {
  widget: DashboardWidgetDto
  /** Si se indica, la tabla actúa de respaldo y se explica por qué. */
  nota?: MotivoVacio
  /** Cross-filtering: solo se propaga a tablas agregadas por categoría. */
  seleccion?: SeleccionChart
}

/**
 * Vista de tabla del widget. Reutiliza los componentes de tabla ya existentes
 * (compartidos con otras páginas) en lugar de duplicar su lógica.
 */
export function TableWidget({ widget, nota, seleccion }: TableWidgetProps) {
  let cuerpo

  if (widget.serieTemporal) {
    // Las series se agrupan por periodo: la selección temporal es 6.9H.2.
    cuerpo = <WidgetSerieTemporal serie={widget.serieTemporal} />
  } else if (widget.comparativa) {
    cuerpo = <WidgetComparativa comparativa={widget.comparativa} seleccion={seleccion} />
  } else if (widget.resultadoActual) {
    cuerpo = <WidgetActual resultado={widget.resultadoActual} seleccion={seleccion} />
  } else {
    return <ChartEmptyState motivo="sin-datos" />
  }

  return (
    <>
      {nota && <NotaVisualizacion motivo={nota} />}
      {cuerpo}
    </>
  )
}
