import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  PASOS_DASHBOARD_INICIAL,
  buscarDashboardInicialExistente,
  crearDashboardInicial,
} from '../../utils/dashboardInicial/orquestadorDashboardInicial'
import type {
  PasoProgresoDashboardInicial,
  ResultadoDashboardInicial,
} from '../../utils/dashboardInicial/orquestadorDashboardInicial'
import { ErrorBanner } from '../ErrorBanner'
import styles from './ImportacionGuiada.module.css'

interface DashboardInicialCtaProps {
  datasetId: number
}

type Estado = 'comprobando' | 'idle' | 'creando' | 'creado' | 'error'

function iconoDe(estado: PasoProgresoDashboardInicial['estado']): { clase: string; simbolo: string } {
  if (estado === 'correcto') return { clase: styles.iconoCorrecto, simbolo: '✓' }
  if (estado === 'error') return { clase: styles.iconoError, simbolo: '✕' }
  if (estado === 'en-curso') return { clase: styles.iconoEnCurso, simbolo: '…' }
  return { clase: styles.iconoPendiente, simbolo: '' }
}

export function DashboardInicialCta({ datasetId }: DashboardInicialCtaProps) {
  const navigate = useNavigate()
  const [estado, setEstado] = useState<Estado>('comprobando')
  const [panelExistenteId, setPanelExistenteId] = useState<number | null>(null)
  const [pasos, setPasos] = useState<PasoProgresoDashboardInicial[]>(PASOS_DASHBOARD_INICIAL)
  const [resultado, setResultado] = useState<ResultadoDashboardInicial | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelado = false
    buscarDashboardInicialExistente(datasetId)
      .then((panel) => {
        if (cancelado) return
        setPanelExistenteId(panel?.id ?? null)
        setEstado('idle')
      })
      .catch(() => {
        if (!cancelado) setEstado('idle')
      })
    return () => {
      cancelado = true
    }
  }, [datasetId])

  const crear = async () => {
    setEstado('creando')
    setError(null)
    setPasos(PASOS_DASHBOARD_INICIAL.map((p) => ({ ...p })))

    const res = await crearDashboardInicial(datasetId, (clave, estadoPaso) => {
      setPasos((actual) => actual.map((p) => (p.clave === clave ? { ...p, estado: estadoPaso } : p)))
    })
    setResultado(res)

    if (res.errorPanel) {
      setError('No se pudo crear el dashboard inicial automáticamente. Puedes continuar en modo avanzado.')
      setEstado('error')
      return
    }

    const huboOmitidos = res.metricasFallidas.length > 0 || res.widgetsFallidos.length > 0
    if (!huboOmitidos && res.panelId !== null) {
      navigate(`/paneles/${res.panelId}/dashboard?inicial=1`)
      return
    }
    setEstado('creado')
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
    <button type="button" className="btn btnPrimary" onClick={crear}>
      Crear dashboard inicial
    </button>
  )
}
