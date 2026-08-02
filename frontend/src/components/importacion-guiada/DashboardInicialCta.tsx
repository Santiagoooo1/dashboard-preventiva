import { Link, useNavigate } from 'react-router'
import { useDashboardInicial } from '../../utils/dashboardInicial/useDashboardInicial'
import type { PasoProgresoDashboardInicial } from '../../utils/dashboardInicial/orquestadorDashboardInicial'
import { ErrorBanner } from '../ErrorBanner'
import styles from './ImportacionGuiada.module.css'

interface DashboardInicialCtaProps {
  datasetId: number
}

function iconoDe(estado: PasoProgresoDashboardInicial['estado']): { clase: string; simbolo: string } {
  if (estado === 'correcto') return { clase: styles.iconoCorrecto, simbolo: '✓' }
  if (estado === 'error') return { clase: styles.iconoError, simbolo: '✕' }
  if (estado === 'en-curso') return { clase: styles.iconoEnCurso, simbolo: '…' }
  return { clase: styles.iconoPendiente, simbolo: '' }
}

export function DashboardInicialCta({ datasetId }: DashboardInicialCtaProps) {
  const navigate = useNavigate()
  // Se usa justo tras una importación que acaba de tener éxito: no hace falta
  // volver a comprobar si hay registros (comprobarRegistros=false), y así un
  // fallo transitorio de esa comprobación extra no bloquea este CTA.
  const { estado, panelExistenteId, pasos, resultado, error, crear } = useDashboardInicial(datasetId, false)

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
      <button type="button" className="btn btnPrimary" disabled>
        Comprobando…
      </button>
    )
  }

  if (estado === 'idle' && panelExistenteId !== null) {
    return (
      <Link className="btn btnPrimary" to={`/paneles/${panelExistenteId}/dashboard?inicial=1`}>
        Ver dashboard inicial
      </Link>
    )
  }

  if (estado === 'creando') {
    return (
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
    )
  }

  if (estado === 'error') {
    return <ErrorBanner mensaje={error} />
  }

  if (estado === 'creado' && resultado) {
    return (
      <div>
        <p>El dashboard se creó con algunos indicadores omitidos.</p>
        {resultado.panelId !== null && (
          <Link className="btn btnPrimary" to={`/paneles/${resultado.panelId}/dashboard?inicial=1`}>
            Ver dashboard
          </Link>
        )}
      </div>
    )
  }

  return (
    <button type="button" className="btn btnPrimary" onClick={alPulsarCrear}>
      Crear dashboard inicial
    </button>
  )
}
