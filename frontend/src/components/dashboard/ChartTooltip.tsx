import styles from './Charts.module.css'

export interface DatosTooltip {
  x: number
  y: number
  etiqueta: string
  valor: string
}

interface ChartTooltipProps {
  datos: DatosTooltip | null
}

export function ChartTooltip({ datos }: ChartTooltipProps) {
  if (!datos) {
    return null
  }
  return (
    <div className={styles.tooltip} style={{ left: `${datos.x}%`, top: `${datos.y}%` }} role="status">
      <span className={styles.tooltipEtiqueta}>{datos.etiqueta}</span>
      <span className={styles.tooltipValor}>{datos.valor}</span>
    </div>
  )
}
