import { useId, useState } from 'react'
import { formatNumber } from '../../utils/formatters'
import { ChartEmptyState } from './ChartEmptyState'
import { ChartTooltip } from './ChartTooltip'
import type { DatosTooltip } from './ChartTooltip'
import styles from './Charts.module.css'

export interface SectorDonut {
  etiqueta: string
  valor: number | null
}

interface PieChartWidgetProps {
  datos: SectorDonut[]
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
    .filter((d): d is { etiqueta: string; valor: number } => d.valor !== null && d.valor !== undefined && d.valor > 0)
    .sort((a, b) => b.valor - a.valor)

  if (validos.length <= MAX_SECTORES) {
    return validos.map((d, i) => ({ ...d, color: COLORES[i] }))
  }

  const principales = validos.slice(0, MAX_SECTORES).map((d, i) => ({ ...d, color: COLORES[i] }))
  const resto = validos.slice(MAX_SECTORES).reduce((suma, d) => suma + d.valor, 0)
  return [...principales, { etiqueta: 'Otros', valor: resto, color: 'var(--chart-neutral)' }]
}

export function PieChartWidget({ datos }: PieChartWidgetProps) {
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

          return (
            <g key={s.etiqueta}>
              <path
                className={styles.marcaHover}
                d={d}
                fill={s.color}
                onMouseEnter={() =>
                  setTooltip({
                    x: (punto(cx, cy, RADIO, medio).x / ANCHO) * 100,
                    y: (punto(cx, cy, RADIO, medio).y / ALTO) * 100,
                    etiqueta: s.etiqueta,
                    valor: `${formatNumber(s.valor)} (${porcentaje}%)`,
                  })
                }
                onMouseLeave={() => setTooltip(null)}
              />
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
      <div className={styles.leyenda}>
        {sectores.map((s) => (
          <span key={s.etiqueta} className={styles.leyendaItem}>
            <span className={styles.leyendaMarca} style={{ backgroundColor: s.color }} />
            {s.etiqueta} · {formatNumber(s.valor)}
          </span>
        ))}
      </div>
    </div>
  )
}
