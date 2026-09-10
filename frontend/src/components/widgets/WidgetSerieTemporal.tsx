import type { PuntoSerieDto, SerieTemporalResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import { formatearEtiquetaCategoria } from '../dashboard/camposFiltroDashboard'
import { formatNumber } from '../../utils/formatters'
import styles from './WidgetSerieTemporal.module.css'

interface WidgetSerieTemporalProps {
  serie: SerieTemporalResponseDto
  /** Unidad de la métrica (p. ej. «%»), para rotular la columna de valor. */
  unidad?: string | null
}

/**
 * Tabla temporal: una fila por periodo (Fase 6.9N).
 *
 * <p>Es la misma serie que alimenta la gráfica, con los mismos números: no hay
 * un segundo cálculo. Enseñarla como tabla existe porque un porcentaje dibujado
 * no dice sobre cuántos casos se calcula, y en vigilancia esa es justo la
 * pregunta siguiente — un 12,5 % sobre 8 intervenciones y un 12,5 % sobre 200
 * no se leen igual.
 */
export function WidgetSerieTemporal({ serie, unidad }: WidgetSerieTemporalProps) {
  if (serie.series) return <TablaSegmentada serie={serie} />
  if (serie.puntos) return <TablaSimple serie={serie} unidad={unidad} />
  return <p>Sin puntos.</p>
}

/** Un periodo sin población no es un cero: se marca como sin datos. */
function celdaValor(punto: PuntoSerieDto, unidad?: string | null): string {
  if (punto.valor === null || punto.valor === undefined) return '—'
  return `${formatNumber(punto.valor)}${unidad ? ` ${unidad}` : ''}`
}

function celdaEntero(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : formatNumber(valor)
}

/**
 * Una métrica con numerador y denominador (porcentaje) permite enseñar de qué
 * se compone cada punto: cuántos se evaluaron, cuántos cumplieron y cuántos no.
 */
function esProporcion(serie: SerieTemporalResponseDto): boolean {
  const puntos = serie.puntos ?? []
  return puntos.some((p) => p.totalDenominador !== null && p.totalNumerador !== null)
}

function TablaSimple({ serie, unidad }: { serie: SerieTemporalResponseDto; unidad?: string | null }) {
  const puntos = serie.puntos ?? []
  const proporcion = esProporcion(serie)

  // «Sin» se calcula aquí y no en el backend porque es exactamente
  // denominador − numerador: una resta de presentación, no otro cálculo.
  const restantes = (p: PuntoSerieDto): string =>
    p.totalDenominador === null || p.totalNumerador === null
      ? '—'
      : formatNumber(p.totalDenominador - p.totalNumerador)

  const columnas = proporcion
    ? [
        { key: 'periodo', header: 'Periodo' },
        { key: 'evaluados', header: 'Evaluados', render: (p: PuntoSerieDto) => celdaEntero(p.totalDenominador) },
        { key: 'positivos', header: 'Casos', render: (p: PuntoSerieDto) => celdaEntero(p.totalNumerador) },
        { key: 'restantes', header: 'Resto', render: restantes },
        { key: 'valor', header: unidad === '%' ? 'Tasa' : 'Valor', render: (p: PuntoSerieDto) => celdaValor(p, unidad) },
      ]
    : [
        { key: 'periodo', header: 'Periodo' },
        { key: 'valor', header: 'Valor', render: (p: PuntoSerieDto) => celdaValor(p, unidad) },
        { key: 'n', header: 'Registros', render: (p: PuntoSerieDto) => celdaEntero(p.totalDenominador) },
      ]

  return (
    <>
      <DataTable columns={columnas} rows={puntos} getRowKey={(p) => p.periodo} />
      {serie.total && (
        <table className={styles.totales}>
          <tbody>
            <tr>
              <th scope="row">Total</th>
              {proporcion && <td>{celdaEntero(serie.total.totalDenominador)}</td>}
              {proporcion && <td>{celdaEntero(serie.total.totalNumerador)}</td>}
              {proporcion && <td>{restantes(serie.total)}</td>}
              <td>{celdaValor(serie.total, unidad)}</td>
              {!proporcion && <td>{celdaEntero(serie.total.totalDenominador)}</td>}
            </tr>
          </tbody>
        </table>
      )}
    </>
  )
}

/**
 * Serie segmentada como matriz: una fila por periodo y una columna por
 * categoría.
 *
 * <p>Antes se pintaba una tabla independiente por categoría, que obliga a
 * comparar entre tablas para responder «¿qué pasó en marzo?». Las columnas
 * salen de los datos, nunca de una lista fija: cada dataset trae las suyas.
 */
function TablaSegmentada({ serie }: { serie: SerieTemporalResponseDto }) {
  const segmentos = serie.series ?? []
  if (segmentos.length === 0) return <p>Sin puntos.</p>

  // Los periodos son los mismos en todos los segmentos (el backend rellena los
  // huecos), así que basta con los del primero.
  const periodos = segmentos[0].puntos.map((p) => p.periodo)

  const valorEn = (indiceSegmento: number, indicePeriodo: number): string => {
    const punto = segmentos[indiceSegmento].puntos[indicePeriodo]
    return punto ? celdaValor(punto) : '—'
  }

  const totalDelPeriodo = (indicePeriodo: number): string => {
    const suma = segmentos.reduce((acc, s) => {
      const v = s.puntos[indicePeriodo]?.valor
      return v === null || v === undefined ? acc : acc + v
    }, 0)
    return formatNumber(suma)
  }

  return (
    <div className={styles.contenedorMatriz}>
      <table className={styles.matriz}>
        <thead>
          <tr>
            <th scope="col">Periodo</th>
            {segmentos.map((s) => (
              <th key={s.etiqueta} scope="col">
                {formatearEtiquetaCategoria(s.etiqueta)}
              </th>
            ))}
            <th scope="col">Total</th>
          </tr>
        </thead>
        <tbody>
          {periodos.map((periodo, i) => (
            <tr key={periodo}>
              <th scope="row">{periodo}</th>
              {segmentos.map((s, j) => (
                <td key={s.etiqueta}>{valorEn(j, i)}</td>
              ))}
              <td>{totalDelPeriodo(i)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">Total</th>
            {segmentos.map((s) => (
              <td key={s.etiqueta}>{s.total ? celdaValor(s.total) : '—'}</td>
            ))}
            <td>{serie.total ? celdaValor(serie.total) : '—'}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
