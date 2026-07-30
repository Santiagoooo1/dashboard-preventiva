import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import type { CatalogoFrontendResponseDto, DashboardPanelResponseDto } from '../api/types'
import { ejecutarDashboard } from '../api/dashboardApi'
import { obtenerDashboardMetadata } from '../api/panelesApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { DashboardFilters, FILTROS_VACIOS, aRequest } from '../components/dashboard/DashboardFilters'
import type { ValoresFiltros } from '../components/dashboard/DashboardFilters'
import { DashboardWidgetRenderer } from '../components/dashboard/DashboardWidgetRenderer'
import styles from './PanelDashboardPage.module.css'

export function PanelDashboardPage() {
  const { panelId } = useParams<{ panelId: string }>()
  const [searchParams] = useSearchParams()
  const esDashboardInicial = searchParams.get('inicial') === '1'

  const [datos, setDatos] = useState<DashboardPanelResponseDto | null>(null)
  const [cargandoInicial, setCargandoInicial] = useState(true)
  const [aplicando, setAplicando] = useState(false)
  const [errorCarga, setErrorCarga] = useState<string | null>(null)

  const [catalogo, setCatalogo] = useState<CatalogoFrontendResponseDto | null>(null)
  const [camposFecha, setCamposFecha] = useState<string[] | null>(null)
  const [errorMetadata, setErrorMetadata] = useState<string | null>(null)

  const [filtros, setFiltros] = useState<ValoresFiltros>(FILTROS_VACIOS)

  const cargarDashboard = useCallback(
    async (valores: ValoresFiltros, inicial: boolean) => {
      if (inicial) setCargandoInicial(true)
      else setAplicando(true)
      setErrorCarga(null)
      try {
        setDatos(await ejecutarDashboard(panelId ?? '', aRequest(valores)))
      } catch (err) {
        setErrorCarga(err instanceof Error ? err.message : 'Error al cargar el dashboard.')
        if (inicial) setDatos(null)
      } finally {
        setCargandoInicial(false)
        setAplicando(false)
      }
    },
    [panelId],
  )

  useEffect(() => {
    cargarDashboard(FILTROS_VACIOS, true)
  }, [cargarDashboard])

  // El catálogo y la metadata son auxiliares: si fallan, los filtros siguen
  // funcionando y el dashboard no se bloquea.
  useEffect(() => {
    const controller = new AbortController()
    getCatalogo(controller.signal)
      .then(setCatalogo)
      .catch(() => {
        if (!controller.signal.aborted) setCatalogo(null)
      })
    obtenerDashboardMetadata(panelId ?? '', controller.signal)
      .then((m) => setCamposFecha(m.camposFechaPermitidos))
      .catch((err: unknown) => {
        if (!controller.signal.aborted) {
          setErrorMetadata(err instanceof Error ? err.message : 'error desconocido')
        }
      })
    return () => controller.abort()
  }, [panelId])

  const limpiar = () => {
    setFiltros(FILTROS_VACIOS)
    cargarDashboard(FILTROS_VACIOS, false)
  }

  return (
    <div className={styles.page}>
      <StateContainer loading={cargandoInicial} error={errorCarga && !datos ? errorCarga : null} empty={datos === null}>
        {datos && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: datos.dataset.codigo, to: `/datasets/${datos.dataset.id}` },
                { label: 'Paneles', to: `/datasets/${datos.dataset.id}/paneles` },
                { label: `Dashboard de ${datos.panel.codigo}` },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>{datos.panel.nombre}</h1>
              <Link
                className="btn btnSecondary"
                to={`/datasets/${datos.dataset.id}/paneles/${datos.panel.id}/widgets`}
              >
                Configurar widgets
              </Link>
            </div>
            <p className={styles.subtitle}>
              {datos.dataset.nombre} · {datos.resumen.widgetsOk} OK / {datos.resumen.widgetsConError} con error de{' '}
              {datos.resumen.totalWidgets} widgets
            </p>

            {esDashboardInicial && (
              <div className={styles.avisoInicial}>
                <p className={styles.avisoInicialTitulo}>Dashboard inicial generado</p>
                <p>
                  Estos indicadores se han creado automáticamente a partir de las columnas detectadas. Puedes
                  modificarlos después desde la gestión avanzada.
                </p>
              </div>
            )}

            <Card title="Filtros del dashboard">
              <DashboardFilters
                valores={filtros}
                onChange={setFiltros}
                onAplicar={() => cargarDashboard(filtros, false)}
                onLimpiar={limpiar}
                granularidades={catalogo?.granularidades ?? []}
                camposFechaPermitidos={camposFecha}
                errorMetadata={errorMetadata}
                cargando={aplicando}
              />
            </Card>

            {errorCarga && <ErrorBanner mensaje={errorCarga} />}

            {datos.widgets.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  Este panel no tiene widgets activos. Configura los widgets para ver resultados.
                </p>
                <Link
                  className="btn btnPrimary"
                  to={`/datasets/${datos.dataset.id}/paneles/${datos.panel.id}/widgets`}
                >
                  Configurar widgets
                </Link>
              </div>
            ) : (
              <div className={`${styles.grid} ${aplicando ? styles.gridCargando : ''}`}>
                {datos.widgets.map((widget) => (
                  <DashboardWidgetRenderer key={widget.panelMetricaId} widget={widget} />
                ))}
              </div>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
