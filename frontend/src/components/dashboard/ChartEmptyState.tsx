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

/**
 * Hay datos y hay categorías/periodos, pero todos valen 0: no hay magnitud que
 * dibujar. Estado NEUTRAL, no de error ni de "sin datos" — un promedio, una
 * suma o un porcentaje pueden ser legítimamente cero, y una barra de altura
 * cero pegada al eje se lee como un gráfico roto.
 *
 * Deliberadamente no afirma nada clínico ("no hubo casos"): solo describe lo
 * que ocurre con la representación.
 */
export function ChartZeroState({ esSerie = false }: { esSerie?: boolean }) {
  return (
    <div className={styles.ceroEstado}>
      <p className={styles.ceroTitulo}>
        {esSerie ? 'Todos los periodos tienen valor 0.' : 'Todas las categorías tienen valor 0.'}
      </p>
      <p className={styles.ceroTexto}>
        {esSerie
          ? 'No hay evolución que representar para el contexto seleccionado.'
          : 'No hay diferencias que representar para el contexto seleccionado.'}
      </p>
    </div>
  )
}
