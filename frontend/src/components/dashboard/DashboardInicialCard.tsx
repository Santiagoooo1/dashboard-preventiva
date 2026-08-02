import { Link, useNavigate } from 'react-router'
import { useDashboardInicial } from '../../utils/dashboardInicial/useDashboardInicial'
import type { PasoProgresoDashboardInicial } from '../../utils/dashboardInicial/orquestadorDashboardInicial'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import styles from './DashboardInicialCard.module.css'

interface DashboardInicialCardProps {
  datasetId: number
}

function iconoDe(estado: PasoProgresoDashboardInicial['estado']): { clase: string; simbolo: string } {
  if (estado === 'correcto') return { clase: styles.iconoCorrecto, simbolo: '✓' }
  if (estado === 'error') return { clase: styles.iconoError, simbolo: '✕' }
  if (estado === 'en-curso') return { clase: styles.iconoEnCurso, simbolo: '…' }
  return { clase: styles.iconoPendiente, simbolo: '' }
}

/**
 * Tarjeta reutilizable para conectar cualquier dataset activo con su
 * dashboard inicial: se usa en DatasetDetailPage y en los listados vacíos de
 * Métricas/Paneles (Fase 6.8G.3). Antes de esta tarjeta, un dataset activo
 * con datos importados solo ofrecía "Nueva métrica"/"Nuevo panel" manuales
 * sin ninguna vía para generar el dashboard automático fuera del flujo de
 * importación guiada.
 *
 * Reutiliza exactamente la misma lógica de creación/comprobación que
 * DashboardInicialCta (vía useDashboardInicial): mismo criterio de
 * idempotencia, mismas métricas/paneles generados.
 */
export function DashboardInicialCard({ datasetId }: DashboardInicialCardProps) {
  const navigate = useNavigate()
  const { estado, panelExistenteId, pasos, resultado, error, crear } = useDashboardInicial(datasetId, true)

  const alPulsarCrear = async () => {
    const res = await crear()
    if (res.errorPanel) return
    const huboOmitidos = res.metricasFallidas.length > 0 || res.widgetsFallidos.length > 0
    if (!huboOmitidos && res.panelId !== null) {
      navigate(`/paneles/${res.panelId}/dashboard?inicial=1`)
    }
  }

  if (estado === 'comprobando') {
    return (
      <Card title="Dashboard inicial">
        <p className="stateLoading" role="status">
          Comprobando…
        </p>
      </Card>
    )
  }

  if (estado === 'sin-datos') {
    return (
      <Card title="Dashboard inicial">
        <p className="stateEmpty">Importa datos antes de crear un dashboard.</p>
      </Card>
    )
  }

  if (estado === 'idle' && panelExistenteId !== null) {
    return (
      <Card title="Dashboard inicial disponible" className={styles.cardListo}>
        <p>Este dataset ya tiene un dashboard inicial generado.</p>
        <Link className="btn btnPrimary" to={`/paneles/${panelExistenteId}/dashboard?inicial=1`}>
          Ver dashboard
        </Link>
      </Card>
    )
  }

  if (estado === 'creando') {
    return (
      <Card title="Dashboard inicial pendiente">
        <ul className={styles.listaProgreso}>
          {pasos.map((paso) => {
            const icono = iconoDe(paso.estado)
            return (
              <li key={paso.clave} className={styles.itemProgreso}>
                <span className={`${styles.iconoProgreso} ${icono.clase}`}>{icono.simbolo}</span>
                <span>{paso.etiqueta}</span>
              </li>
            )
          })}
        </ul>
      </Card>
    )
  }

  if (estado === 'error') {
    return (
      <Card title="Dashboard inicial pendiente">
        <ErrorBanner mensaje={error} />
        <button type="button" className="btn btnPrimary" onClick={alPulsarCrear}>
          Reintentar
        </button>
      </Card>
    )
  }

  if (estado === 'creado' && resultado) {
    return (
      <Card title="Dashboard inicial disponible" className={styles.cardListo}>
        <p>
          El dashboard se creó{' '}
          {resultado.metricasFallidas.length > 0 || resultado.widgetsFallidos.length > 0
            ? 'con algunos indicadores omitidos.'
            : 'correctamente.'}
        </p>
        {resultado.panelId !== null && (
          <Link className="btn btnPrimary" to={`/paneles/${resultado.panelId}/dashboard?inicial=1`}>
            Ver dashboard
          </Link>
        )}
      </Card>
    )
  }

  return (
    <Card title="Dashboard inicial pendiente">
      <p>
        Este dataset ya tiene datos importados. Puedes generar automáticamente un primer dashboard con métricas
        básicas para explorarlo.
      </p>
      <button type="button" className="btn btnPrimary" onClick={alPulsarCrear}>
        Crear dashboard inicial
      </button>
    </Card>
  )
}
