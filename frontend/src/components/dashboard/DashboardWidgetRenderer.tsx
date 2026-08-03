import { useId, useState } from 'react'
import type { ChangeEvent } from 'react'
import type { DashboardWidgetDto, TipoVisualizacion } from '../../api/types'
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
import {
  ETIQUETA_TIPO_RESULTADO,
  normalizarTipoVisualizacion,
  visualizacionesCompatibles,
} from './visualizacionesCompatibles'
import styles from './DashboardWidgetRenderer.module.css'

const MAX_SERIES_LINEA = 4

const ETIQUETA_VISUALIZACION: Record<string, string> = {
  KPI: 'Indicador',
  TARJETA: 'Tarjeta',
  TABLA: 'Tabla',
  BARRAS: 'Barras',
  LINEAS: 'Líneas',
  DONUT: 'Donut',
  PIE: 'Circular',
}

interface DashboardWidgetRendererProps {
  widget: DashboardWidgetDto
  /**
   * Ausente en contextos de solo lectura (no aplica aquí, pero deja la puerta
   * abierta). Cuando está presente, el widget ofrece el selector "Vista" para
   * cambiar tipoVisualizacion y persiste el cambio a través de esta función.
   */
  onCambiarVisualizacion?: (panelMetricaId: number, nuevoTipo: TipoVisualizacion) => Promise<void>
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
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
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

export function DashboardWidgetRenderer({ widget, onCambiarVisualizacion }: DashboardWidgetRendererProps) {
  const conError = widget.estado === 'ERROR'
  const [guardando, setGuardando] = useState(false)
  const [errorVista, setErrorVista] = useState<string | null>(null)
  const selectId = useId()

  const opciones = onCambiarVisualizacion ? visualizacionesCompatibles(widget) : []
  const puedeElegirVista = opciones.length > 0

  const alCambiarVista = async (e: ChangeEvent<HTMLSelectElement>) => {
    if (!onCambiarVisualizacion) return
    const nuevoTipo = e.target.value as TipoVisualizacion
    setGuardando(true)
    setErrorVista(null)
    try {
      await onCambiarVisualizacion(widget.panelMetricaId, nuevoTipo)
    } catch (err) {
      setErrorVista(err instanceof Error ? err.message : 'No se pudo cambiar la visualización.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className={styles.widget} style={{ ['--span' as string]: clampAncho(widget.ancho) }}>
      <div className={`${styles.card} ${conError ? styles.cardError : ''}`}>
        <div className={styles.cardHeader}>
          <div className={styles.cardHeaderText}>
            <h3 className={styles.cardTitle}>{widget.titulo}</h3>
            <p className={styles.cardMeta}>
              {ETIQUETA_TIPO_RESULTADO[widget.tipoResultado] ?? widget.tipoResultado}
              {widget.descripcion && <span className={styles.cardDescripcion}> · {widget.descripcion}</span>}
            </p>
          </div>
          {puedeElegirVista ? (
            <label className={styles.selectorVista}>
              <span className={styles.selectorVistaEtiqueta} id={`${selectId}-label`}>
                Vista
              </span>
              <select
                className={styles.selectVista}
                aria-labelledby={`${selectId}-label`}
                value={normalizarTipoVisualizacion(widget.tipoVisualizacion)}
                disabled={guardando}
                onChange={alCambiarVista}
              >
                {opciones.map((o) => (
                  <option key={o.valor} value={o.valor}>
                    {o.etiqueta}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <span className={styles.badgeTipo}>
              {ETIQUETA_VISUALIZACION[widget.tipoVisualizacion] ?? widget.tipoVisualizacion}
            </span>
          )}
        </div>
        {errorVista && (
          <p className={styles.errorVista} role="alert">
            {errorVista}
          </p>
        )}
        <div className={styles.cardBody}>{cuerpoWidget(widget)}</div>
      </div>
    </div>
  )
}
