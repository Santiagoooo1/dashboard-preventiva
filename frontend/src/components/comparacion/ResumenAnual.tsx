import type { ComparacionInteranualResponseDto } from '../../api/types'
import { formatNumber } from '../../utils/formatters'
import styles from './Comparacion.module.css'

interface ResumenAnualProps {
  comparacion: ComparacionInteranualResponseDto
}

/**
 * Una fila por año con su total (Fase 6.9P).
 *
 * <p>Sale directamente de `SerieAnualDto.total`, que el backend calcula sobre
 * los acumulados del año. No se promedian las filas mensuales: con 1/100 en
 * enero y 1/50 en febrero, la media daría 1,5 % y la tasa del año es 1,33 %.
 */
export function ResumenAnual({ comparacion }: ResumenAnualProps) {
  const { series, tipoComparacion } = comparacion
  if (series.length === 0) return null

  if (tipoComparacion === 'RESUMEN_NUMERICO') {
    return (
      <div className={styles.contenedorTabla}>
        <table className={styles.matriz}>
          <caption className={styles.leyenda}>Resumen por año</caption>
          <thead>
            <tr>
              <th scope="col">Año</th>
              <th scope="col">N</th>
              <th scope="col">Media</th>
              <th scope="col">Mínimo</th>
              <th scope="col">Máximo</th>
            </tr>
          </thead>
          <tbody>
            {series.map((s) => (
              <tr key={`${s.datasetId}-${s.anio}`}>
                <th scope="row">{s.etiqueta}</th>
                <td>{s.total.denominador ?? '—'}</td>
                <td>{s.total.media !== null ? formatNumber(s.total.media) : '—'}</td>
                <td>{s.total.minimo !== null ? formatNumber(s.total.minimo) : '—'}</td>
                <td>{s.total.maximo !== null ? formatNumber(s.total.maximo) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  if (tipoComparacion === 'RECUENTO') {
    // Un recuento no es una fracción: sin evaluados ni denominador, que se
    // leerían como si esto fuera una tasa.
    return (
      <div className={styles.contenedorTabla}>
        <table className={styles.matriz}>
          <caption className={styles.leyenda}>Resumen por año</caption>
          <thead>
            <tr>
              <th scope="col">Año</th>
              <th scope="col">Casos</th>
            </tr>
          </thead>
          <tbody>
            {series.map((s) => (
              <tr key={`${s.datasetId}-${s.anio}`}>
                <th scope="row">{s.etiqueta}</th>
                <td>{s.total.valor !== null ? formatNumber(s.total.valor) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  if (tipoComparacion === 'DISTRIBUCION') {
    return (
      <div className={styles.contenedorTabla}>
        <table className={styles.matriz}>
          <caption className={styles.leyenda}>Resumen por año</caption>
          <thead>
            <tr>
              <th scope="col">Año</th>
              <th scope="col">Casos clasificados</th>
            </tr>
          </thead>
          <tbody>
            {series.map((s) => (
              <tr key={`${s.datasetId}-${s.anio}`}>
                <th scope="row">{s.etiqueta}</th>
                <td>{formatNumber(s.total.valor ?? 0)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  return (
    <div className={styles.contenedorTabla}>
      <table className={styles.matriz}>
        <caption className={styles.leyenda}>Resumen por año</caption>
        <thead>
          <tr>
            <th scope="col">Año</th>
            <th scope="col">Evaluados</th>
            <th scope="col">Casos</th>
            <th scope="col">Tasa</th>
          </tr>
        </thead>
        <tbody>
          {series.map((s) => (
            <tr key={`${s.datasetId}-${s.anio}`}>
              <th scope="row">{s.etiqueta}</th>
              <td>{s.total.denominador ?? '—'}</td>
              <td>{s.total.numerador ?? '—'}</td>
              <td>{s.total.valor !== null ? `${formatNumber(s.total.valor)} %` : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
