import { useId, useState } from 'react'
import { formatNumber } from '../../utils/formatters'
import { acortar, crearBandas, crearEscalaY } from './charts/escalas'
import { ChartEmptyState } from './ChartEmptyState'
import { ChartTooltip } from './ChartTooltip'
import type { DatosTooltip } from './ChartTooltip'
import styles from './Charts.module.css'

/** Degradado vertical sutil sobre el mismo tono de la serie: mismo dato, más profundidad visual. */
function GradienteBarra({ id }: { id: string }) {
  return (
    <defs>
      <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stopColor="var(--chart-series-1)" stopOpacity={1} />
        <stop offset="100%" stopColor="var(--chart-series-1)" stopOpacity={0.75} />
      </linearGradient>
    </defs>
  )
}

export interface DatoBarra {
  /** Texto ya formateado para mostrar (booleanos como Sí/No). */
  etiqueta: string
  valor: number | null
  /**
   * Valor técnico tal como lo devolvió el backend ("true", "false", el código
   * real de la categoría). Se conserva sin traducir porque es el que deberán
   * usar los filtros y la selección gráfica de 6.9H: el texto mostrado nunca
   * debe viajar como valor.
   */
  valorOriginal?: string
}

/** Interacción de cross-filtering. Ausente = gráfico puramente informativo. */
export interface SeleccionChart {
  /** Nombre legible del campo, para los textos accesibles. */
  etiquetaCampo: string
  /** valorOriginal actualmente seleccionado en este gráfico, si lo hay. */
  valorSeleccionado: string | null
  onSeleccionar: (valorOriginal: string, etiquetaVisible: string) => void
}

interface BarChartWidgetProps {
  datos: DatoBarra[]
  seleccion?: SeleccionChart
}

/**
 * Props comunes de un elemento seleccionable dentro de un SVG. Solo se aplican
 * cuando el dato tiene `valorOriginal`: sin valor técnico no se puede construir
 * un filtro fiable, así que ese elemento no se vuelve interactivo.
 */
function propsSeleccion(
  dato: DatoBarra,
  seleccion: SeleccionChart | undefined,
  valorFormateado: string,
) {
  if (!seleccion || !dato.valorOriginal) return { interactivo: false as const, activa: false }

  const activa = seleccion.valorSeleccionado === dato.valorOriginal
  return {
    interactivo: true as const,
    activa,
    props: {
      role: 'button',
      tabIndex: 0,
      'aria-pressed': activa,
      'aria-label': activa
        ? `${seleccion.etiquetaCampo}: ${dato.etiqueta} seleccionado. Pulsar para quitar.`
        : `Seleccionar ${seleccion.etiquetaCampo}: ${dato.etiqueta}. Total: ${valorFormateado}.`,
      onClick: () => seleccion.onSeleccionar(dato.valorOriginal!, dato.etiqueta),
      onKeyDown: (e: { key: string; preventDefault: () => void }) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          seleccion.onSeleccionar(dato.valorOriginal!, dato.etiqueta)
        }
      },
    },
  }
}

/** Clase de la marca según su estado dentro del gráfico de origen. */
function claseMarca(interactivo: boolean, activa: boolean, haySeleccion: boolean): string {
  if (!interactivo) return styles.marcaHover
  if (activa) return `${styles.marcaSeleccionable} ${styles.marcaSeleccionada}`
  // Atenuada, pero sigue siendo interactiva: el origen conserva todas sus
  // categorías para no perder el contexto desde el que se navegó.
  return `${styles.marcaSeleccionable} ${haySeleccion ? styles.marcaAtenuada : ''}`
}

const ANCHO = 480
const ALTO = 260

// Con muchas categorías o etiquetas largas, las barras verticales apilan
// texto ilegible en el eje X; en ese caso se giran a horizontal.
function convieneHorizontal(datos: DatoBarra[]): boolean {
  return datos.length > 6 || datos.some((d) => d.etiqueta.length > 12)
}

export function BarChartWidget({ datos, seleccion }: BarChartWidgetProps) {
  const [tooltip, setTooltip] = useState<DatosTooltip | null>(null)
  const gradId = useId()

  const conValor = datos.filter((d) => d.valor !== null && d.valor !== undefined)
  if (conValor.length === 0) {
    return <ChartEmptyState motivo="sin-datos" />
  }

  const horizontal = convieneHorizontal(datos)
  const sub = { datos, tooltip, setTooltip, gradId, seleccion }
  return horizontal ? <BarrasHorizontales {...sub} /> : <BarrasVerticales {...sub} />
}

interface SubProps {
  datos: DatoBarra[]
  tooltip: DatosTooltip | null
  setTooltip: (t: DatosTooltip | null) => void
  gradId: string
  seleccion?: SeleccionChart
}

function BarrasVerticales({ datos, tooltip, setTooltip, gradId, seleccion }: SubProps) {
  const margen = { top: 18, right: 12, bottom: 34, left: 44 }
  const anchoUtil = ANCHO - margen.left - margen.right
  const altoUtil = ALTO - margen.top - margen.bottom

  const escala = crearEscalaY(
    datos.map((d) => d.valor ?? 0),
    altoUtil,
    margen.top,
  )
  const bandas = crearBandas(datos.length, anchoUtil, margen.left)
  const yCero = escala.y(Math.max(escala.min, 0))

  return (
    <div className={styles.contenedor}>
      <svg className={styles.svg} viewBox={`0 0 ${ANCHO} ${ALTO}`} role="img">
        <GradienteBarra id={gradId} />
        {escala.ticks.map((t) => (
          <g key={t}>
            <line
              className={styles.grid}
              x1={margen.left}
              x2={ANCHO - margen.right}
              y1={escala.y(t)}
              y2={escala.y(t)}
            />
            <text className={styles.textoEje} x={margen.left - 6} y={escala.y(t) + 3} textAnchor="end">
              {formatNumber(t, 1)}
            </text>
          </g>
        ))}
        <line className={styles.eje} x1={margen.left} x2={ANCHO - margen.right} y1={yCero} y2={yCero} />

        {datos.map((d, i) => {
          if (d.valor === null || d.valor === undefined) return null
          const yValor = escala.y(d.valor)
          const y = Math.min(yValor, yCero)
          const alto = Math.max(1, Math.abs(yCero - yValor))
          const x = bandas.centro(i) - bandas.grosor / 2
          const sel = propsSeleccion(d, seleccion, formatNumber(d.valor))
          return (
            <g key={`${d.etiqueta}-${i}`}>
              <rect
                className={claseMarca(sel.interactivo, sel.activa, Boolean(seleccion?.valorSeleccionado))}
                x={x}
                y={y}
                width={bandas.grosor}
                height={alto}
                rx={6}
                fill={sel.activa ? 'var(--color-selection)' : `url(#${gradId})`}
                {...(sel.props ?? {})}
                onMouseEnter={() =>
                  setTooltip({
                    x: (bandas.centro(i) / ANCHO) * 100,
                    y: (y / ALTO) * 100,
                    etiqueta: d.etiqueta,
                    valor: formatNumber(d.valor),
                  })
                }
                onMouseLeave={() => setTooltip(null)}
              >
                <title>{`${d.etiqueta}: ${formatNumber(d.valor)}`}</title>
              </rect>
              <text className={styles.etiquetaDirecta} x={bandas.centro(i)} y={y - 5} textAnchor="middle">
                {formatNumber(d.valor)}
              </text>
              <text
                className={styles.textoEje}
                x={bandas.centro(i)}
                y={ALTO - margen.bottom + 14}
                textAnchor="middle"
                aria-label={d.etiqueta}
              >
                {/* <title> hijo = tooltip nativo del navegador sobre el texto
                    recortado; el valor completo nunca se pierde (lo necesitará
                    la selección gráfica de 6.9H). */}
                <title>{d.etiqueta}</title>
                {acortar(d.etiqueta, 10)}
              </text>
            </g>
          )
        })}
      </svg>
      <ChartTooltip datos={tooltip} />
    </div>
  )
}

function BarrasHorizontales({ datos, tooltip, setTooltip, gradId, seleccion }: SubProps) {
  const margen = { top: 8, right: 48, bottom: 8, left: 110 }
  // Con una sola categoría (cohorte de un paciente, por ejemplo) una fila de
  // 26px en un lienzo alto deja la tarjeta casi vacía: se engorda la fila para
  // que la barra siga siendo legible sin inventar categorías que no existen.
  const altoFila = datos.length === 1 ? 56 : 26
  const alto = margen.top + margen.bottom + datos.length * altoFila
  const anchoUtil = ANCHO - margen.left - margen.right

  const valores = datos.map((d) => d.valor ?? 0)
  const max = Math.max(...valores, 0) || 1
  const x = (valor: number) => margen.left + (valor / max) * anchoUtil

  return (
    <div className={styles.contenedor}>
      <svg className={styles.svg} viewBox={`0 0 ${ANCHO} ${alto}`} role="img">
        <GradienteBarra id={gradId} />
        <line className={styles.eje} x1={margen.left} x2={margen.left} y1={margen.top} y2={alto - margen.bottom} />
        {datos.map((d, i) => {
          const yFila = margen.top + i * altoFila
          const grosor = altoFila - 8
          const sel = propsSeleccion(d, seleccion, formatNumber(d.valor))
          return (
            <g key={`${d.etiqueta}-${i}`}>
              <text
                className={styles.textoEje}
                x={margen.left - 6}
                y={yFila + grosor / 2 + 4}
                textAnchor="end"
                aria-label={d.etiqueta}
              >
                <title>{d.etiqueta}</title>
                {acortar(d.etiqueta, 16)}
              </text>
              {d.valor !== null && d.valor !== undefined && (
                <>
                  <rect
                    className={claseMarca(sel.interactivo, sel.activa, Boolean(seleccion?.valorSeleccionado))}
                    x={margen.left}
                    y={yFila}
                    width={Math.max(1, x(d.valor) - margen.left)}
                    height={grosor}
                    rx={6}
                    fill={sel.activa ? 'var(--color-selection)' : `url(#${gradId})`}
                    {...(sel.props ?? {})}
                    onMouseEnter={() =>
                      setTooltip({
                        x: (x(d.valor!) / ANCHO) * 100,
                        y: (yFila / alto) * 100,
                        etiqueta: d.etiqueta,
                        valor: formatNumber(d.valor),
                      })
                    }
                    onMouseLeave={() => setTooltip(null)}
                  />
                  <text
                    className={styles.etiquetaDirecta}
                    x={x(d.valor) + 6}
                    y={yFila + grosor / 2 + 4}
                  >
                    {formatNumber(d.valor)}
                  </text>
                </>
              )}
            </g>
          )
        })}
      </svg>
      <ChartTooltip datos={tooltip} />
    </div>
  )
}
