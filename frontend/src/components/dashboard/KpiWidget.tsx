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

  return (
    <div className={styles.kpi}>
      {descripcion && <p className={styles.sub}>{descripcion}</p>}
      <p className={`${styles.valor} ${compacto ? styles.valorCompacto : ''}`}>
        {formatNumber(resultado.valor)}
        {resultado.unidad && <span className={styles.unidad}>{resultado.unidad}</span>}
      </p>
      {tieneTotales && (
        <p className={styles.sub}>
          {resultado.totalNumerador} de {resultado.totalDenominador}
        </p>
      )}
    </div>
  )
}
