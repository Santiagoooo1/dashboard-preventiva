import type { DashboardWidgetDto } from '../../api/types'
import { Card } from '../Card'
import cardStyles from '../Card.module.css'
import { clampAncho } from '../../utils/formatters'
import { WidgetActual } from './WidgetActual'
import { WidgetSerieTemporal } from './WidgetSerieTemporal'
import { WidgetComparativa } from './WidgetComparativa'
import { WidgetError } from './WidgetError'

interface WidgetCardProps {
  widget: DashboardWidgetDto
}

export function WidgetCard({ widget }: WidgetCardProps) {
  const gridColumn = `span ${clampAncho(widget.ancho)}`

  let body
  if (widget.estado === 'ERROR') {
    body = <WidgetError widget={widget} />
  } else if (widget.tipoResultado === 'SERIE_TEMPORAL' && widget.serieTemporal) {
    body = <WidgetSerieTemporal serie={widget.serieTemporal} />
  } else if (widget.tipoResultado === 'COMPARATIVA' && widget.comparativa) {
    body = <WidgetComparativa comparativa={widget.comparativa} />
  } else if (widget.resultadoActual) {
    body = <WidgetActual resultado={widget.resultadoActual} />
  } else {
    body = <p>Sin datos.</p>
  }

  return (
    <div style={{ gridColumn }}>
      <Card
        title={widget.titulo}
        subtitle={`${widget.tipoVisualizacion} · ${widget.tipoResultado}`}
        className={widget.estado === 'ERROR' ? cardStyles.cardError : cardStyles.cardOk}
      >
        {widget.descripcion && <p>{widget.descripcion}</p>}
        {body}
      </Card>
    </div>
  )
}
