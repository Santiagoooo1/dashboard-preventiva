import type { CeldaComparacionDto, ComparacionInteranualResponseDto } from '../../api/types'
import { formatNumber } from '../../utils/formatters'
import { formatearEtiquetaCategoria } from '../dashboard/camposFiltroDashboard'
import styles from './Comparacion.module.css'

interface MatrizInteranualProps {
  comparacion: ComparacionInteranualResponseDto
}

const NOMBRE_MES: Record<string, string> = {
  '01': 'Enero', '02': 'Febrero', '03': 'Marzo', '04': 'Abril',
  '05': 'Mayo', '06': 'Junio', '07': 'Julio', '08': 'Agosto',
  '09': 'Septiembre', '10': 'Octubre', '11': 'Noviembre', '12': 'Diciembre',
}

function etiquetaPeriodo(periodo: string): string {
  return NOMBRE_MES[periodo] ?? periodo
}

/**
 * Matriz interanual: una fila por periodo y una columna por año (Fase 6.9P).
 *
 * <p>Todos los números vienen calculados del backend, incluida la fila TOTAL y
 * la variación. Aquí no se recalcula ni un porcentaje: si el frontend rehiciera
 * la división, tendríamos dos fórmulas que se separarían a la primera
 * corrección, que es justo lo que se evitó en 6.9N y 6.9O.
 */
export function MatrizInteranual({ comparacion }: MatrizInteranualProps) {
  const { series, periodos } = comparacion
  if (series.length === 0) return null

  const esTasa = comparacion.tipoComparacion === 'TASA'
  const ultima = series[series.length - 1]
  const hayVariacion = series.length > 1 && ultima.variacion !== null

  const celdaDe = (indiceSerie: number, indicePeriodo: number) =>
    series[indiceSerie].celdas[indicePeriodo]

  const contenido = (celda: CeldaComparacionDto | undefined) => {
    if (!celda) return <span className={styles.sinDato}>—</span>
    if (esTasa) {
      if (celda.valor === null) return <span className={styles.sinDato}>—</span>
      return (
        <>
          <span className={styles.valor}>{formatNumber(celda.valor)} %</span>
          {/* El tamaño de la población es parte del dato: un 1,8 % de 4/222 y
              otro de 1/55 no se leen igual. */}
          {celda.denominador !== null && (
            <span className={styles.fraccion}>
              {celda.numerador} / {celda.denominador}
            </span>
          )}
        </>
      )
    }
    if (celda.valor === null) return <span className={styles.sinDato}>—</span>
    return <span className={styles.valor}>{formatNumber(celda.valor)}</span>
  }

  const variacionDe = (indicePeriodo: number) => {
    const celda = ultima.variacion?.[indicePeriodo]
    if (!celda || celda.valor === null) return <span className={styles.sinDato}>—</span>
    return <span className={claseVariacion(celda.valor)}>{textoVariacion(celda.valor, ultima.unidadVariacion)}</span>
  }

  return (
    <div className={styles.contenedorTabla}>
      <table className={styles.matriz}>
        <caption className={styles.leyenda}>
          {comparacion.etiquetaConcepto}
          {hayVariacion && ` · Δ ${ultima.etiqueta} frente a ${series[series.length - 2].etiqueta}`}
        </caption>
        <thead>
          <tr>
            <th scope="col">Periodo</th>
            {series.map((s) => (
              <th key={`${s.datasetId}-${s.anio}`} scope="col">
                {s.etiqueta}
              </th>
            ))}
            {hayVariacion && <th scope="col">Δ</th>}
          </tr>
        </thead>
        <tbody>
          {periodos.map((periodo, i) => (
            <tr key={periodo}>
              <th scope="row">{etiquetaPeriodo(periodo)}</th>
              {series.map((s, j) => (
                <td key={`${s.datasetId}-${s.anio}`}>{contenido(celdaDe(j, i))}</td>
              ))}
              {hayVariacion && <td>{variacionDe(i)}</td>}
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">Total</th>
            {series.map((s) => (
              <td key={`${s.datasetId}-${s.anio}`}>{contenido(s.total)}</td>
            ))}
            {hayVariacion && (
              <td>
                {ultima.variacionTotal && ultima.variacionTotal.valor !== null ? (
                  <span className={claseVariacion(ultima.variacionTotal.valor)}>
                    {textoVariacion(ultima.variacionTotal.valor, ultima.unidadVariacion)}
                  </span>
                ) : (
                  <span className={styles.sinDato}>—</span>
                )}
              </td>
            )}
          </tr>
        </tfoot>
      </table>
      {esTasa && (
        <p className={styles.nota}>
          Bajo cada porcentaje, los casos sobre las intervenciones con el dato documentado. El total de cada año se
          calcula sobre sus acumulados, no promediando los meses.
        </p>
      )}
    </div>
  )
}

/**
 * La diferencia de dos porcentajes va en PUNTOS PORCENTUALES.
 *
 * De 3,1 % a 1,8 % son −1,3 pp. Decir «−41,9 %» responde a otra pregunta y se
 * lee como si la tasa hubiera pasado a ser 41,9 %.
 */
function textoVariacion(valor: number, unidad: string): string {
  const signo = valor > 0 ? '+' : ''
  return `${signo}${formatNumber(valor)}${unidad === 'pp' ? ' pp' : ''}`
}

/** Ni verde ni rojo: subir o bajar no es bueno o malo sin saber qué se mide. */
function claseVariacion(valor: number): string {
  if (valor > 0) return styles.variacionSube
  if (valor < 0) return styles.variacionBaja
  return styles.variacionIgual
}

/** Matriz de categorías: una tabla por año, con la unión de categorías como columnas. */
export function MatrizCategoriasInteranual({ comparacion }: MatrizInteranualProps) {
  const { series, periodos, categorias } = comparacion
  if (series.length === 0 || categorias.length === 0) return null

  const valorDe = (celda: CeldaComparacionDto | undefined, categoria: string): string => {
    const encontrada = celda?.categorias?.find((c) => c.categoria === categoria)
    // 0 es un cero real: el concepto existía y no hubo casos. El backend ya
    // rechaza la comparación cuando el concepto falta, así que aquí no hay
    // ambigüedad entre «cero» y «no comparable».
    return encontrada ? formatNumber(encontrada.valor) : '0'
  }

  return (
    <>
      {series.map((serie) => (
        <div key={`${serie.datasetId}-${serie.anio}`} className={styles.bloqueAnio}>
          <h4 className={styles.tituloAnio}>{serie.etiqueta}</h4>
          <div className={styles.contenedorTabla}>
            <table className={styles.matriz}>
              <thead>
                <tr>
                  <th scope="col">Periodo</th>
                  {categorias.map((c) => (
                    <th key={c} scope="col">
                      {formatearEtiquetaCategoria(c)}
                    </th>
                  ))}
                  <th scope="col">Total</th>
                </tr>
              </thead>
              <tbody>
                {periodos.map((periodo, i) => (
                  <tr key={periodo}>
                    <th scope="row">{etiquetaPeriodo(periodo)}</th>
                    {categorias.map((c) => (
                      <td key={c}>{valorDe(serie.celdas[i], c)}</td>
                    ))}
                    <td>{formatNumber(serie.celdas[i]?.valor ?? 0)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">Total</th>
                  {categorias.map((c) => (
                    <td key={c}>{valorDe(serie.total, c)}</td>
                  ))}
                  <td>{formatNumber(serie.total.valor ?? 0)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      ))}
    </>
  )
}
