import { Link } from 'react-router'
import styles from './DashboardHeader.module.css'

interface DashboardHeaderProps {
  panelNombre: string
  panelCodigo: string
  datasetNombre: string
  datasetId: number
  totalWidgets: number
  widgetsOk: number
  widgetsConError: number
  esDashboardInicial: boolean
  configurarWidgetsHref: string
}

/**
 * Cabecera visual del dashboard (Fase 6.9B.1): antes era un `<h1>` suelto +
 * un `<p>` de subtítulo pegados arriba de la página. Ahora es un bloque
 * diferenciado (tarjeta propia) con título, dataset asociado, resumen de
 * widgets como chips de estado, badge de "Dashboard inicial" cuando aplica,
 * y la acción de configurar como secundaria — para que lo primero que se lea
 * sea el dashboard en sí, no un enlace de administración.
 */
export function DashboardHeader({
  panelNombre,
  panelCodigo,
  datasetNombre,
  datasetId,
  totalWidgets,
  widgetsOk,
  widgetsConError,
  esDashboardInicial,
  configurarWidgetsHref,
}: DashboardHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.top}>
        <div className={styles.titleBlock}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>{panelNombre}</h1>
            {esDashboardInicial && <span className={styles.badgeInicial}>Dashboard inicial</span>}
          </div>
          <p className={styles.meta}>
            Dataset{' '}
            <Link className={styles.metaLink} to={`/datasets/${datasetId}`}>
              {datasetNombre}
            </Link>
            <span className={styles.metaSep}>·</span>
            {panelCodigo}
          </p>
        </div>
        <Link className={`btn btnSecondary ${styles.actionSecondary}`} to={configurarWidgetsHref}>
          Configurar widgets
        </Link>
      </div>

      <div className={styles.stats}>
        <span className={`${styles.chip} ${styles.chipOk}`}>
          <span className={styles.chipDot} />
          {widgetsOk} al día
        </span>
        {widgetsConError > 0 && (
          <span className={`${styles.chip} ${styles.chipError}`}>
            <span className={styles.chipDot} />
            {widgetsConError} con error
          </span>
        )}
        <span className={styles.statMeta}>{totalWidgets} indicador{totalWidgets === 1 ? '' : 'es'} en total</span>
      </div>

      {esDashboardInicial && (
        <p className={styles.avisoInicial}>
          Estos indicadores se han creado automáticamente a partir de las columnas detectadas. Puedes
          modificarlos después desde la gestión avanzada.
        </p>
      )}
    </header>
  )
}
