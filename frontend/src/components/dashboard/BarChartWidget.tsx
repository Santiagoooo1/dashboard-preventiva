import { useState } from 'react'
import { formatNumber } from '../../utils/formatters'
import { acortar, crearBandas, crearEscalaY } from './charts/escalas'
import { ChartEmptyState } from './ChartEmptyState'
import { ChartTooltip } from './ChartTooltip'
import type { DatosTooltip } from './ChartTooltip'
import styles from './Charts.module.css'

export interface DatoBarra {
  etiqueta: string
  valor: number | null
}

interface BarChartWidgetProps {
  datos: DatoBarra[]
}

const ANCHO = 480
const ALTO = 260

// Con muchas categorías o etiquetas largas, las barras verticales apilan
// texto ilegible en el eje X; en ese caso se giran a horizontal.
function convieneHorizontal(datos: DatoBarra[]): boolean {
  return datos.length > 6 || datos.some((d) => d.etiqueta.length > 12)
}

export function BarChartWidget({ datos }: BarChartWidgetProps) {
  const [tooltip, setTooltip] = useState<DatosTooltip | null>(null)

  const conValor = datos.filter((d) => d.valor !== null && d.valor !== undefined)
  if (conValor.length === 0) {
    return <ChartEmptyState motivo="sin-datos" />
  }

  const horizontal = convieneHorizontal(datos)
  return horizontal ? (
    <BarrasHorizontales datos={datos} tooltip={tooltip} setTooltip={setTooltip} />
  ) : (
    <BarrasVerticales datos={datos} tooltip={tooltip} setTooltip={setTooltip} />
  )
}

interface SubProps {
  datos: DatoBarra[]
  tooltip: DatosTooltip | null
  setTooltip: (t: DatosTooltip | null) => void
}

function BarrasVerticales({ datos, tooltip, setTooltip }: SubProps) {
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
          return (
            <g key={`${d.etiqueta}-${i}`}>
              <rect
                className={styles.marcaHover}
                x={x}
                y={y}
                width={bandas.grosor}
                height={alto}
                rx={4}
                fill="var(--chart-series-1)"
                onMouseEnter={() =>
                  setTooltip({
                    x: (bandas.centro(i) / ANCHO) * 100,
                    y: (y / ALTO) * 100,
                    etiqueta: d.etiqueta,
                    valor: formatNumber(d.valor),
                  })
                }
                onMouseLeave={() => setTooltip(null)}
              />
              <text className={styles.etiquetaDirecta} x={bandas.centro(i)} y={y - 5} textAnchor="middle">
                {formatNumber(d.valor)}
              </text>
              <text
                className={styles.textoEje}
                x={bandas.centro(i)}
                y={ALTO - margen.bottom + 14}
                textAnchor="middle"
              >
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

function BarrasHorizontales({ datos, tooltip, setTooltip }: SubProps) {
  const margen = { top: 8, right: 48, bottom: 8, left: 110 }
  const altoFila = 26
  const alto = margen.top + margen.bottom + datos.length * altoFila
  const anchoUtil = ANCHO - margen.left - margen.right

  const valores = datos.map((d) => d.valor ?? 0)
  const max = Math.max(...valores, 0) || 1
  const x = (valor: number) => margen.left + (valor / max) * anchoUtil

  return (
    <div className={styles.contenedor}>
      <svg className={styles.svg} viewBox={`0 0 ${ANCHO} ${alto}`} role="img">
        <line className={styles.eje} x1={margen.left} x2={margen.left} y1={margen.top} y2={alto - margen.bottom} />
        {datos.map((d, i) => {
          const yFila = margen.top + i * altoFila
          const grosor = altoFila - 8
          return (
            <g key={`${d.etiqueta}-${i}`}>
              <text className={styles.textoEje} x={margen.left - 6} y={yFila + grosor / 2 + 4} textAnchor="end">
                {acortar(d.etiqueta, 16)}
              </text>
              {d.valor !== null && d.valor !== undefined && (
                <>
                  <rect
                    className={styles.marcaHover}
                    x={margen.left}
                    y={yFila}
                    width={Math.max(1, x(d.valor) - margen.left)}
                    height={grosor}
                    rx={4}
                    fill="var(--chart-series-1)"
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
