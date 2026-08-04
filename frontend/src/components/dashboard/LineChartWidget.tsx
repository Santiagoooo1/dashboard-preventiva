import { useId, useState } from 'react'
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
  /** Texto ya formateado para mostrar (booleanos como Sí/No). */
  etiqueta: string
  puntos: PuntoLinea[]
  /** Valor técnico original del backend, para filtros y la selección de 6.9H. */
  valorOriginal?: string
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
): { lineas: string[]; aislados: number[]; segmentos: { i: number; x: number; y: number }[][] } {
  const lineas: string[] = []
  const aislados: number[] = []
  const segmentos: { i: number; x: number; y: number }[][] = []
  let actual: { d: string; i: number; x: number; y: number }[] = []

  const cerrar = () => {
    if (actual.length > 1) {
      lineas.push(actual.map((t) => t.d).join(' '))
      segmentos.push(actual.map(({ i, x: px, y: py }) => ({ i, x: px, y: py })))
    } else if (actual.length === 1) {
      aislados.push(actual[0].i)
    }
    actual = []
  }

  puntos.forEach((p, i) => {
    if (p.valor === null || p.valor === undefined) {
      cerrar()
      return
    }
    const px = x(i)
    const py = y(p.valor)
    actual.push({ d: `${actual.length === 0 ? 'M' : 'L'}${px},${py}`, i, x: px, y: py })
  })
  cerrar()
  return { lineas, aislados, segmentos }
}

/** Área rellena bajo un tramo de línea (solo con una única serie, para no solapar rellenos). */
function areaDeTramo(segmento: { x: number; y: number }[], yBase: number): string {
  const ida = segmento.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
  const vuelta = `L${segmento[segmento.length - 1].x},${yBase} L${segmento[0].x},${yBase} Z`
  return `${ida} ${vuelta}`
}

export function LineChartWidget({ series, mostrarLeyenda }: LineChartWidgetProps) {
  const [tooltip, setTooltip] = useState<DatosTooltip | null>(null)
  const gradId = useId()

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

  const yBase = escala.y(Math.max(escala.min, 0))

  // Un único punto en toda la serie (cohorte de un solo registro): la
  // representación es correcta, pero reservar la altura completa de una
  // evolución para un solo dato deja la tarjeta medio vacía. Se limita la
  // altura sin tocar el viewBox, así el eje temporal, la fecha y el valor
  // siguen intactos. Es solo presentación: no se persiste nada.
  const puntosConValor = series.reduce(
    (total, s) => total + s.puntos.filter((p) => p.valor !== null && p.valor !== undefined).length,
    0,
  )
  const compacto = puntosConValor === 1

  return (
    <div className={styles.contenedor}>
      <svg
        className={`${styles.svg} ${compacto ? styles.svgCompacto : ''}`}
        viewBox={`0 0 ${ANCHO} ${ALTO}`}
        role="img"
      >
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={COLORES[0]} stopOpacity={0.28} />
            <stop offset="100%" stopColor={COLORES[0]} stopOpacity={0} />
          </linearGradient>
        </defs>
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
              aria-label={periodo}
            >
              <title>{periodo}</title>
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
          const { lineas, aislados, segmentos } = tramos(serie.puntos, x, escala.y)

          return (
            <g key={serie.etiqueta}>
              {series.length === 1 &&
                segmentos.map((segmento, i) => (
                  <path key={`area-${i}`} d={areaDeTramo(segmento, yBase)} fill={`url(#${gradId})`} stroke="none" />
                ))}
              {lineas.map((d, i) => (
                <path key={i} d={d} fill="none" stroke={color} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
              ))}
              {/* Puntos sin vecino con valor: nunca se traza línea entre ellos
                  (sería inventar una evolución). Con una cohorte de un solo
                  registro el punto es lo único que hay, así que se dibuja
                  grande y con su propio texto accesible. */}
              {aislados.map((i) => (
                <circle
                  key={`aislado-${i}`}
                  cx={x(i)}
                  cy={escala.y(serie.puntos[i].valor!)}
                  r={5}
                  fill={color}
                  stroke="var(--color-surface)"
                  strokeWidth={1.5}
                >
                  <title>{`${serie.puntos[i].periodo}: ${formatNumber(serie.puntos[i].valor)}`}</title>
                </circle>
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
                  {/* Con un único punto, el valor a secas no dice a qué periodo
                      corresponde: se acompaña de la fecha. */}
                  {serie.puntos.filter((p) => p.valor !== null && p.valor !== undefined).length === 1
                    ? `${ultimoConValor.p.periodo}: ${formatNumber(ultimoConValor.p.valor)}`
                    : formatNumber(ultimoConValor.p.valor)}
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
