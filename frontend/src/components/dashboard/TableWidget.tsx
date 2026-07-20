import type { DashboardWidgetDto } from '../../api/types'
import { WidgetActual } from '../widgets/WidgetActual'
import { WidgetComparativa } from '../widgets/WidgetComparativa'
import { WidgetSerieTemporal } from '../widgets/WidgetSerieTemporal'
import { ChartEmptyState, NotaVisualizacion } from './ChartEmptyState'
import type { MotivoVacio } from './ChartEmptyState'

interface TableWidgetProps {
  widget: DashboardWidgetDto
  /** Si se indica, la tabla actúa de respaldo y se explica por qué. */
  nota?: MotivoVacio
}

/**
 * Vista de tabla del widget. Reutiliza los componentes de tabla ya existentes
 * (compartidos con otras páginas) en lugar de duplicar su lógica.
 */
export function TableWidget({ widget, nota }: TableWidgetProps) {
  let cuerpo

  if (widget.serieTemporal) {
    cuerpo = <WidgetSerieTemporal serie={widget.serieTemporal} />
  } else if (widget.comparativa) {
    cuerpo = <WidgetComparativa comparativa={widget.comparativa} />
  } else if (widget.resultadoActual) {
    cuerpo = <WidgetActual resultado={widget.resultadoActual} />
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
