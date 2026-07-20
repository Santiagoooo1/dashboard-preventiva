import { useState } from 'react'
import { formatNumber } from '../../utils/formatters'
import { acortar, crearEscalaY } from './charts/escalas'
import { ChartEmptyState } from './ChartEmptyState'
import { ChartTooltip } from './ChartTooltip'
import type { DatosTooltip } from './ChartTooltip'
import styles from './Charts.module.css'

export interface PuntoLinea {
  periodo: string
  valor: number | null
}

export interface SerieLinea {
  etiqueta: string
  puntos: PuntoLinea[]
}

interface LineChartWidgetProps {
  series: SerieLinea[]
  /** Con una sola serie no hace falta leyenda: el título ya la nombra. */
  mostrarLeyenda: boolean
}

const ANCHO = 520
const ALTO = 260
const COLORES = [
  'var(--chart-series-1)',
  'var(--chart-series-2)',
  'var(--chart-series-3)',
  'var(--chart-series-4)',
]

/**
 * Parte la línea en los valores nulos: un hueco clínico (por ejemplo un promedio
 * sin datos) no es un cero y no se interpola.
 *
 * Un punto aislado entre dos nulos no puede dibujarse como trazo, así que se
 * devuelve aparte para pintarlo como marca; si no, ese dato desaparecería.
 */
function tramos(
  puntos: PuntoLinea[],
  x: (i: number) => number,
  y: (v: number) => number,
): { lineas: string[]; aislados: number[] } {
  const lineas: string[] = []
  const aislados: number[] = []
  let actual: { d: string; i: number }[] = []

  const cerrar = () => {
    if (actual.length > 1) lineas.push(actual.map((t) => t.d).join(' '))
    else if (actual.length === 1) aislados.push(actual[0].i)
    actual = []
  }

  puntos.forEach((p, i) => {
    if (p.valor === null || p.valor === undefined) {
      cerrar()
      return
    }
    actual.push({ d: `${actual.length === 0 ? 'M' : 'L'}${x(i)},${y(p.valor)}`, i })
  })
  cerrar()
  return { lineas, aislados }
}

export function LineChartWidget({ series, mostrarLeyenda }: LineChartWidgetProps) {
  const [tooltip, setTooltip] = useState<DatosTooltip | null>(null)

  const hayDatos = series.some((s) => s.puntos.some((p) => p.valor !== null && p.valor !== undefined))
  if (!hayDatos) {
    return <ChartEmptyState motivo="sin-datos" />
  }

  const margen = { top: 18, right: 54, bottom: 34, left: 44 }
  const anchoUtil = ANCHO - margen.left - margen.right
  const altoUtil = ALTO - margen.top - margen.bottom

  const periodos = series[0]?.puntos.map((p) => p.periodo) ?? []
  const todosValores = series.flatMap((s) =>
    s.puntos.map((p) => p.valor).filter((v): v is number => v !== null && v !== undefined),
  )
  const escala = crearEscalaY(todosValores, altoUtil, margen.top)
  const n = Math.max(periodos.length, 1)
  const x = (i: number) => (n === 1 ? margen.left + anchoUtil / 2 : margen.left + (anchoUtil / (n - 1)) * i)

  // Con muchos periodos se muestran etiquetas alternas para que no se solapen.
  const saltoEtiquetas = Math.ceil(n / 8)

  return (
    <div className={styles.contenedor}>
      <svg className={styles.svg} viewBox={`0 0 ${ANCHO} ${ALTO}`} role="img">
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
        <line
          className={styles.eje}
          x1={margen.left}
          x2={ANCHO - margen.right}
          y1={margen.top + altoUtil}
          y2={margen.top + altoUtil}
        />

        {periodos.map((periodo, i) =>
          i % saltoEtiquetas === 0 ? (
            <text
              key={periodo}
              className={styles.textoEje}
              x={x(i)}
              y={ALTO - margen.bottom + 14}
              textAnchor="middle"
            >
              {acortar(periodo, 8)}
            </text>
          ) : null,
        )}

        {series.map((serie, indiceSerie) => {
          const color = COLORES[indiceSerie % COLORES.length]
          const ultimoConValor = [...serie.puntos]
            .map((p, i) => ({ p, i }))
            .reverse()
            .find(({ p }) => p.valor !== null && p.valor !== undefined)
          const { lineas, aislados } = tramos(serie.puntos, x, escala.y)

          return (
            <g key={serie.etiqueta}>
              {lineas.map((d, i) => (
                <path key={i} d={d} fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" />
              ))}
              {aislados.map((i) => (
                <circle key={`aislado-${i}`} cx={x(i)} cy={escala.y(serie.puntos[i].valor!)} r={3} fill={color} />
              ))}
              {serie.puntos.map((p, i) =>
                p.valor === null || p.valor === undefined ? null : (
                  <circle
                    key={`${serie.etiqueta}-${p.periodo}`}
                    className={styles.marcaHover}
                    cx={x(i)}
                    cy={escala.y(p.valor)}
                    r={5}
                    fill={color}
                    fillOpacity={0}
                    stroke={color}
                    strokeOpacity={0}
                    onMouseEnter={(e) => {
                      e.currentTarget.setAttribute('fill-opacity', '1')
                      setTooltip({
                        x: (x(i) / ANCHO) * 100,
                        y: (escala.y(p.valor!) / ALTO) * 100,
                        etiqueta: `${serie.etiqueta} · ${p.periodo}`,
                        valor: formatNumber(p.valor),
                      })
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.setAttribute('fill-opacity', '0')
                      setTooltip(null)
                    }}
                  />
                ),
              )}
              {ultimoConValor && (
                <text
                  className={styles.etiquetaDirecta}
                  x={x(ultimoConValor.i) + 6}
                  y={escala.y(ultimoConValor.p.valor!) + 3}
                >
                  {formatNumber(ultimoConValor.p.valor)}
                </text>
              )}
            </g>
          )
        })}
      </svg>
      <ChartTooltip datos={tooltip} />
      {mostrarLeyenda && (
        <div className={styles.leyenda}>
          {series.map((serie, i) => (
            <span key={serie.etiqueta} className={styles.leyendaItem}>
              <span
                className={styles.leyendaMarca}
                style={{ backgroundColor: COLORES[i % COLORES.length] }}
              />
              {serie.etiqueta}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
