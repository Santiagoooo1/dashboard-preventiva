import { useId, useState } from 'react'
import { formatNumber } from '../../utils/formatters'
import { ChartEmptyState } from './ChartEmptyState'
import { ChartTooltip } from './ChartTooltip'
import type { DatosTooltip } from './ChartTooltip'
import type { SeleccionChart } from './BarChartWidget'
import styles from './Charts.module.css'

export interface SectorDonut {
  etiqueta: string
  valor: number | null
  /** Valor técnico del backend. Sin él, el sector no es seleccionable. */
  valorOriginal?: string
}

interface PieChartWidgetProps {
  datos: SectorDonut[]
  seleccion?: SeleccionChart
}

const ANCHO = 320
const ALTO = 240
const RADIO = 92
const GROSOR = 34
const MAX_SECTORES = 4
const COLORES = [
  'var(--chart-series-1)',
  'var(--chart-series-2)',
  'var(--chart-series-3)',
  'var(--chart-series-4)',
]

function punto(cx: number, cy: number, radio: number, angulo: number) {
  return { x: cx + radio * Math.cos(angulo), y: cy + radio * Math.sin(angulo) }
}

/** Arco anular entre dos ángulos. */
function arco(cx: number, cy: number, rExt: number, rInt: number, desde: number, hasta: number): string {
  const grande = hasta - desde > Math.PI ? 1 : 0
  const e1 = punto(cx, cy, rExt, desde)
  const e2 = punto(cx, cy, rExt, hasta)
  const i2 = punto(cx, cy, rInt, hasta)
  const i1 = punto(cx, cy, rInt, desde)
  return [
    `M${e1.x},${e1.y}`,
    `A${rExt},${rExt} 0 ${grande} 1 ${e2.x},${e2.y}`,
    `L${i2.x},${i2.y}`,
    `A${rInt},${rInt} 0 ${grande} 0 ${i1.x},${i1.y}`,
    'Z',
  ].join(' ')
}

/**
 * Agrupa a partir del quinto sector: la paleta categórica está validada hasta
 * cuatro tonos, y más sectores dejan de distinguirse de un vistazo.
 */
function prepararSectores(datos: SectorDonut[]) {
  const validos = datos
    .filter(
      (d): d is { etiqueta: string; valor: number; valorOriginal?: string } =>
        d.valor !== null && d.valor !== undefined && d.valor > 0,
    )
    .sort((a, b) => b.valor - a.valor)

  if (validos.length <= MAX_SECTORES) {
    return validos.map((d, i) => ({ ...d, color: COLORES[i] }))
  }

  const principales = validos.slice(0, MAX_SECTORES).map((d, i) => ({ ...d, color: COLORES[i] }))
  const resto = validos.slice(MAX_SECTORES).reduce((suma, d) => suma + d.valor, 0)
  // "Otros" agrega varias categorías: no representa un valor técnico único, así
  // que se deja SIN `valorOriginal` y por tanto no es seleccionable. Filtrar
  // por "Otros" no significaría nada para el backend.
  return [...principales, { etiqueta: 'Otros', valor: resto, color: 'var(--chart-neutral)', valorOriginal: undefined }]
}

interface SectorPreparado {
  etiqueta: string
  valor: number
  color: string
  valorOriginal?: string
}

/** Mismo contrato que en barras: sin `valorOriginal`, el sector no es interactivo. */
function propsSeleccionSector(
  sector: SectorPreparado,
  seleccion: SeleccionChart | undefined,
  valorFormateado: string,
) {
  if (!seleccion || !sector.valorOriginal) return { interactivo: false as const, activa: false }

  const activa = seleccion.valorSeleccionado === sector.valorOriginal
  return {
    interactivo: true as const,
    activa,
    props: {
      role: 'button',
      tabIndex: 0,
      'aria-pressed': activa,
      'aria-label': activa
        ? `${seleccion.etiquetaCampo}: ${sector.etiqueta} seleccionado. Pulsar para quitar.`
        : `Seleccionar ${seleccion.etiquetaCampo}: ${sector.etiqueta}. Total: ${valorFormateado}.`,
      onClick: () => seleccion.onSeleccionar(sector.valorOriginal!, sector.etiqueta),
      onKeyDown: (e: { key: string; preventDefault: () => void }) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          seleccion.onSeleccionar(sector.valorOriginal!, sector.etiqueta)
        }
      },
    },
  }
}

function claseSector(interactivo: boolean, activa: boolean, haySeleccion: boolean): string {
  if (!interactivo) return styles.marcaHover
  if (activa) return `${styles.marcaSeleccionable} ${styles.marcaSeleccionada}`
  return `${styles.marcaSeleccionable} ${haySeleccion ? styles.marcaAtenuada : ''}`
}

export function PieChartWidget({ datos, seleccion }: PieChartWidgetProps) {
  const [tooltip, setTooltip] = useState<DatosTooltip | null>(null)
  const filtroId = useId()

  const sectores = prepararSectores(datos)
  const total = sectores.reduce((suma, s) => suma + s.valor, 0)

  if (sectores.length === 0 || total === 0) {
    return <ChartEmptyState motivo="sin-datos" />
  }

  const cx = ANCHO / 2
  const cy = ALTO / 2
  let angulo = -Math.PI / 2

  return (
    <div className={styles.contenedor}>
      <svg className={styles.svg} viewBox={`0 0 ${ANCHO} ${ALTO}`} role="img">
        <defs>
          <filter id={filtroId} x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="2" stdDeviation="3" floodColor="#0f172a" floodOpacity="0.16" />
          </filter>
        </defs>
        <g filter={`url(#${filtroId})`}>
        {sectores.map((s) => {
          const proporcion = s.valor / total
          const desde = angulo
          // Hueco de 2px entre sectores para que los rellenos no se toquen.
          const hasta = angulo + proporcion * Math.PI * 2
          angulo = hasta
          const separacion = sectores.length > 1 ? 0.012 : 0
          const d = arco(cx, cy, RADIO, RADIO - GROSOR, desde + separacion, Math.max(desde + separacion, hasta - separacion))
          const medio = (desde + hasta) / 2
          const posEtiqueta = punto(cx, cy, RADIO + 14, medio)
          const porcentaje = (proporcion * 100).toFixed(proporcion < 0.1 ? 1 : 0)

          const sel = propsSeleccionSector(s, seleccion, `${formatNumber(s.valor)} (${porcentaje}%)`)

          return (
            <g key={s.etiqueta}>
              <path
                className={claseSector(sel.interactivo, sel.activa, Boolean(seleccion?.valorSeleccionado))}
                d={d}
                fill={sel.activa ? 'var(--color-selection)' : s.color}
                {...(sel.props ?? {})}
                onMouseEnter={() =>
                  setTooltip({
                    x: (punto(cx, cy, RADIO, medio).x / ANCHO) * 100,
                    y: (punto(cx, cy, RADIO, medio).y / ALTO) * 100,
                    etiqueta: s.etiqueta,
                    valor: `${formatNumber(s.valor)} (${porcentaje}%)`,
                  })
                }
                onMouseLeave={() => setTooltip(null)}
              >
                <title>{`${s.etiqueta}: ${formatNumber(s.valor)} (${porcentaje}%)`}</title>
              </path>
              {proporcion >= 0.06 && (
                <text
                  className={styles.etiquetaDirecta}
                  x={posEtiqueta.x}
                  y={posEtiqueta.y}
                  textAnchor={posEtiqueta.x >= cx ? 'start' : 'end'}
                >
                  {porcentaje}%
                </text>
              )}
            </g>
          )
        })}
        </g>
        <text className={styles.donutTotal} x={cx} y={cy + 2}>
          {formatNumber(total)}
        </text>
        <text className={styles.donutTotalEtiqueta} x={cx} y={cy + 18}>
          Total
        </text>
      </svg>
      <ChartTooltip datos={tooltip} />
      {/* Leyenda sincronizada: pulsar una entrada produce exactamente la misma
          selección que pulsar su sector. */}
      <div className={styles.leyenda}>
        {sectores.map((s) => {
          const sel = propsSeleccionSector(s, seleccion, formatNumber(s.valor))
          const contenido = (
            <>
              <span
                className={styles.leyendaMarca}
                style={{ backgroundColor: sel.activa ? 'var(--color-selection)' : s.color }}
              />
              {s.etiqueta} · {formatNumber(s.valor)}
            </>
          )

          if (!sel.interactivo) {
            return (
              <span key={s.etiqueta} className={styles.leyendaItem}>
                {contenido}
              </span>
            )
          }

          return (
            <button
              key={s.etiqueta}
              type="button"
              className={`${styles.leyendaItem} ${styles.leyendaItemSeleccionable} ${
                sel.activa ? styles.leyendaItemActiva : ''
              }`}
              {...sel.props}
            >
              {contenido}
            </button>
          )
        })}
      </div>
    </div>
  )
}
