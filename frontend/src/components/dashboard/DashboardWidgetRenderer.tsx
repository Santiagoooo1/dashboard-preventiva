import type { DashboardWidgetDto } from '../../api/types'
import { Card } from '../Card'
import cardStyles from '../Card.module.css'
import { clampAncho } from '../../utils/formatters'
import { WidgetError } from '../widgets/WidgetError'
import { BarChartWidget } from './BarChartWidget'
import type { DatoBarra } from './BarChartWidget'
import { LineChartWidget } from './LineChartWidget'
import type { SerieLinea } from './LineChartWidget'
import { PieChartWidget } from './PieChartWidget'
import { KpiWidget } from './KpiWidget'
import { TableWidget } from './TableWidget'
import { ChartEmptyState } from './ChartEmptyState'
import styles from './DashboardWidgetRenderer.module.css'

const MAX_SERIES_LINEA = 4

interface DashboardWidgetRendererProps {
  widget: DashboardWidgetDto
}

/** Categorías (etiqueta/valor) disponibles en el widget, vengan de donde vengan. */
function categorias(widget: DashboardWidgetDto): DatoBarra[] | null {
  if (widget.comparativa) {
    return widget.comparativa.items.map((i) => ({ etiqueta: i.etiqueta, valor: i.valor }))
  }
  if (widget.resultadoActual?.items) {
    return widget.resultadoActual.items.map((i) => ({ etiqueta: i.etiqueta, valor: i.valor }))
  }
  return null
}

/** Series temporales normalizadas: la simple es una serie con el título del widget. */
function seriesTemporales(widget: DashboardWidgetDto): SerieLinea[] | null {
  const serie = widget.serieTemporal
  if (!serie) return null
  if (serie.series) {
    return serie.series.map((s) => ({
      etiqueta: s.etiqueta,
      puntos: s.puntos.map((p) => ({ periodo: p.periodo, valor: p.valor })),
    }))
  }
  if (serie.puntos) {
    return [{ etiqueta: widget.titulo, puntos: serie.puntos.map((p) => ({ periodo: p.periodo, valor: p.valor })) }]
  }
  return null
}

function cuerpoWidget(widget: DashboardWidgetDto) {
  if (widget.estado === 'ERROR') {
    return <WidgetError widget={widget} />
  }

  const cats = categorias(widget)
  const series = seriesTemporales(widget)
  const actual = widget.resultadoActual
  const tieneValorSimple = actual && actual.valor !== null && actual.valor !== undefined && !actual.items

  switch (widget.tipoVisualizacion) {
    case 'KPI':
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
      return <TableWidget widget={widget} />

    case 'TARJETA':
      if (tieneValorSimple) return <KpiWidget resultado={actual} descripcion={widget.descripcion} />
      return <TableWidget widget={widget} />

    case 'TABLA':
      if (tieneValorSimple) return <KpiWidget resultado={actual} compacto />
      return <TableWidget widget={widget} />

    case 'BARRAS':
      if (cats) return <BarChartWidget datos={cats} />
      if (series) {
        // Una serie temporal en barras se representa por periodo; con varios
        // segmentos las barras se solaparían, así que cae a tabla.
        if (series.length === 1) {
          return <BarChartWidget datos={series[0].puntos.map((p) => ({ etiqueta: p.periodo, valor: p.valor }))} />
        }
        return <TableWidget widget={widget} nota="incompatible" />
      }
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
      return <ChartEmptyState motivo="sin-datos" />

    case 'LINEAS':
      if (series) {
        if (series.length > MAX_SERIES_LINEA) {
          return <TableWidget widget={widget} nota="incompatible" />
        }
        return <LineChartWidget series={series} mostrarLeyenda={series.length > 1} />
      }
      return <TableWidget widget={widget} nota="incompatible" />

    case 'PIE':
    case 'DONUT':
      if (cats) return <PieChartWidget datos={cats} />
      if (series) return <TableWidget widget={widget} nota="incompatible" />
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
      return <ChartEmptyState motivo="sin-datos" />

    default:
      return <TableWidget widget={widget} />
  }
}

export function DashboardWidgetRenderer({ widget }: DashboardWidgetRendererProps) {
  return (
    <div className={styles.widget} style={{ ['--span' as string]: clampAncho(widget.ancho) }}>
      <Card
        title={widget.titulo}
        subtitle={`${widget.tipoVisualizacion} · ${widget.tipoResultado}`}
        className={widget.estado === 'ERROR' ? cardStyles.cardError : cardStyles.cardOk}
      >
        {cuerpoWidget(widget)}
      </Card>
    </div>
  )
}
