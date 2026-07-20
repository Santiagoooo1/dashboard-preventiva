import styles from './Charts.module.css'

export type MotivoVacio = 'sin-datos' | 'incompatible'

const MENSAJES: Record<MotivoVacio, string> = {
  'sin-datos': 'No hay datos para mostrar con los filtros actuales.',
  incompatible: 'Este widget no puede representarse con esta visualización.',
}

interface ChartEmptyStateProps {
  motivo: MotivoVacio
}

export function ChartEmptyState({ motivo }: ChartEmptyStateProps) {
  return <p className={styles.vacio}>{MENSAJES[motivo]}</p>
}

/** Mismo texto, en línea, para acompañar a la tabla de respaldo. */
export function NotaVisualizacion({ motivo }: ChartEmptyStateProps) {
  return <p className={styles.nota}>{MENSAJES[motivo]}</p>
}
