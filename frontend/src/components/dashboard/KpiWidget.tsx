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
  if (resultado.valor === null || resultado.valor === undefined) {
    return <ChartEmptyState motivo="sin-datos" />
  }

  const tieneTotales = resultado.totalNumerador !== null && resultado.totalNumerador !== undefined
  const proporcion =
    tieneTotales && resultado.totalDenominador ? resultado.totalNumerador! / resultado.totalDenominador : null

  return (
    <div className={styles.kpi}>
      <p className={`${styles.valor} ${compacto ? styles.valorCompacto : ''}`}>
        {formatNumber(resultado.valor)}
        {resultado.unidad && <span className={styles.unidad}>{resultado.unidad}</span>}
      </p>
      {descripcion && <p className={styles.sub}>{descripcion}</p>}
      {tieneTotales && (
        <div className={styles.detalle}>
          {proporcion !== null && (
            <span className={styles.barra}>
              <span className={styles.barraRelleno} style={{ width: `${Math.min(100, proporcion * 100)}%` }} />
            </span>
          )}
          <span className={styles.detalleTexto}>
            {resultado.totalNumerador} de {resultado.totalDenominador}
          </span>
        </div>
      )}
    </div>
  )
}
