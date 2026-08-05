import type { ResultadoMetricaResponseDto } from '../../api/types'
import { formatNumber } from '../../utils/formatters'
import { ChartEmptyState } from './ChartEmptyState'
import styles from './KpiWidget.module.css'

interface KpiWidgetProps {
  resultado: ResultadoMetricaResponseDto
  descripcion?: string | null
  compacto?: boolean
}

export function KpiWidget({ resultado, descripcion, compacto }: KpiWidgetProps) {
  // Sin base evaluable: el denominador es cero, o no hay ningún valor informado
  // sobre el que promediar. NO es un 0 % ni un 0 — decirlo así haría pasar por
  // resultado clínico algo que solo significa «aquí no hay nada que medir».
  if (resultado.estado === 'SIN_BASE_EVALUABLE') {
    return (
      <div className={styles.kpi}>
        <p className={`${styles.valorSinBase} ${compacto ? styles.valorCompacto : ''}`}>Sin base evaluable</p>
        <p className={styles.sub}>
          {resultado.etiquetaDenominador
            ? `Ningún registro cumple: ${resultado.etiquetaDenominador}.`
            : 'Ningún registro entra en el denominador con el contexto actual.'}
        </p>
      </div>
    )
  }

  // Resultado no numérico: una fecha (primera/última) o el nombre de la
  // categoría más frecuente. Se pinta como texto; el número, si lo hay, baja al
  // detalle en vez de sustituirlo.
  const esTextual = resultado.valor === null || resultado.valor === undefined

  if (esTextual && !resultado.valorTexto) {
    return <ChartEmptyState motivo="sin-datos" />
  }

  const tieneTotales = resultado.totalNumerador !== null && resultado.totalNumerador !== undefined
  const proporcion =
    tieneTotales && resultado.totalDenominador ? resultado.totalNumerador! / resultado.totalDenominador : null

  return (
    <div className={styles.kpi}>
      <p className={`${styles.valor} ${compacto ? styles.valorCompacto : ''}`}>
        {esTextual ? (
          <span className={styles.valorTexto}>{resultado.valorTexto}</span>
        ) : (
          <>
            {formatNumber(resultado.valor)}
            {resultado.unidad && <span className={styles.unidad}>{resultado.unidad}</span>}
          </>
        )}
      </p>
      {descripcion && <p className={styles.sub}>{descripcion}</p>}
      {tieneTotales && (
        <div className={styles.detalle}>
          {proporcion !== null && (
            <span className={styles.barra}>
              <span className={styles.barraRelleno} style={{ width: `${Math.min(100, proporcion * 100)}%` }} />
            </span>
          )}
          {/* Numerador y denominador SIEMPRE visibles: un porcentaje clínico del
              que no se sabe sobre cuántos casos se calcula no es interpretable. */}
          <span className={styles.detalleTexto}>
            {resultado.totalNumerador} de {resultado.totalDenominador}
          </span>
          {(resultado.etiquetaNumerador || resultado.etiquetaDenominador) && (
            <span className={styles.detalleEtiquetas}>
              {resultado.etiquetaNumerador ?? 'Numerador'} sobre {resultado.etiquetaDenominador ?? 'denominador'}
            </span>
          )}
        </div>
      )}
    </div>
  )
}
