import type { ComparacionInteranualResponseDto } from '../../api/types'
import { formatNumber } from '../../utils/formatters'
import styles from './Comparacion.module.css'

interface GraficaInteranualProps {
  comparacion: ComparacionInteranualResponseDto
}

const COLORES = [
  'var(--chart-1, #2563eb)',
  'var(--chart-2, #16a34a)',
  'var(--chart-3, #d97706)',
  'var(--chart-4, #9333ea)',
  'var(--chart-5, #dc2626)',
  'var(--chart-6, #0891b2)',
]

const ANCHO = 720
const ALTO = 280
const MARGEN = { arriba: 16, derecha: 16, abajo: 40, izquierda: 52 }

const NOMBRE_MES_CORTO: Record<string, string> = {
  '01': 'Ene', '02': 'Feb', '03': 'Mar', '04': 'Abr', '05': 'May', '06': 'Jun',
  '07': 'Jul', '08': 'Ago', '09': 'Sep', '10': 'Oct', '11': 'Nov', '12': 'Dic',
}

/**
 * Una línea por año (Fase 6.9P).
 *
 * <p>Consume exactamente la misma respuesta que la matriz: los puntos son las
 * celdas que la tabla enseña como números. Si la gráfica calculara lo suyo,
 * podría dibujar una tendencia que la tabla no respalda.
 *
 * <p>Los periodos sin población evaluable no se dibujan y cortan la línea, en
 * vez de bajarla a cero: un mes sin intervenciones no es un mes sin infección.
 */
export function GraficaInteranual({ comparacion }: GraficaInteranualProps) {
  const { series, periodos, tipoComparacion } = comparacion
  if (series.length === 0 || periodos.length === 0) return null

  const esTasa = tipoComparacion === 'TASA'

  const valores = series.flatMap((s) => s.celdas.map((c) => c.valor)).filter((v): v is number => v !== null)
  if (valores.length === 0) {
    return <p className={styles.nota}>No hay valores que dibujar en el periodo seleccionado.</p>
  }

  const maximo = Math.max(...valores, 0)
  // Un poco de aire arriba para que el punto más alto no toque el borde.
  const techo = maximo === 0 ? 1 : maximo * 1.15

  const anchoUtil = ANCHO - MARGEN.izquierda - MARGEN.derecha
  const altoUtil = ALTO - MARGEN.arriba - MARGEN.abajo
  const x = (i: number) =>
    MARGEN.izquierda + (periodos.length === 1 ? anchoUtil / 2 : (anchoUtil * i) / (periodos.length - 1))
  const y = (valor: number) => MARGEN.arriba + altoUtil - (altoUtil * valor) / techo

  const marcas = [0, techo / 2, techo]

  return (
    <div className={styles.contenedorGrafica}>
      <svg
        viewBox={`0 0 ${ANCHO} ${ALTO}`}
        className={styles.grafica}
        role="img"
        aria-label={`Evolución de ${comparacion.etiquetaConcepto} comparada entre ${series
          .map((s) => s.etiqueta)
          .join(', ')}`}
      >
        {marcas.map((marca) => (
          <g key={marca}>
            <line
              x1={MARGEN.izquierda}
              x2={ANCHO - MARGEN.derecha}
              y1={y(marca)}
              y2={y(marca)}
              className={styles.lineaGuia}
            />
            <text x={MARGEN.izquierda - 8} y={y(marca) + 4} textAnchor="end" className={styles.etiquetaEje}>
              {formatNumber(marca)}
              {esTasa ? '%' : ''}
            </text>
          </g>
        ))}

        {periodos.map((periodo, i) => (
          <text key={periodo} x={x(i)} y={ALTO - MARGEN.abajo + 18} textAnchor="middle" className={styles.etiquetaEje}>
            {NOMBRE_MES_CORTO[periodo] ?? periodo}
          </text>
        ))}

        {series.map((serie, indice) => {
          const color = COLORES[indice % COLORES.length]
          // Cada tramo continuo se dibuja por separado: un hueco corta la línea
          // en vez de unir dos meses que no son consecutivos con datos.
          const tramos: { i: number; valor: number }[][] = []
          let actual: { i: number; valor: number }[] = []
          serie.celdas.forEach((celda, i) => {
            if (celda.valor === null) {
              if (actual.length > 0) tramos.push(actual)
              actual = []
            } else {
              actual.push({ i, valor: celda.valor })
            }
          })
          if (actual.length > 0) tramos.push(actual)

          return (
            <g key={`${serie.datasetId}-${serie.anio}`}>
              {tramos.map((tramo, t) => (
                <polyline
                  key={t}
                  points={tramo.map((p) => `${x(p.i)},${y(p.valor)}`).join(' ')}
                  fill="none"
                  stroke={color}
                  strokeWidth={2}
                />
              ))}
              {tramos.flat().map((p) => (
                <circle key={p.i} cx={x(p.i)} cy={y(p.valor)} r={3.5} fill={color}>
                  <title>
                    {`${serie.etiqueta} · ${NOMBRE_MES_CORTO[periodos[p.i]] ?? periodos[p.i]}: ${formatNumber(p.valor)}${
                      esTasa ? ' %' : ''
                    }`}
                  </title>
                </circle>
              ))}
            </g>
          )
        })}
      </svg>

      <ul className={styles.leyendaSeries}>
        {series.map((serie, indice) => (
          <li key={`${serie.datasetId}-${serie.anio}`}>
            <span className={styles.muestraColor} style={{ background: COLORES[indice % COLORES.length] }} />
            {/* La etiqueta la decide el backend: con dos datasets del mismo año
                lleva el nombre delante para poder distinguirlos. */}
            {serie.etiqueta}
          </li>
        ))}
      </ul>
    </div>
  )
}
