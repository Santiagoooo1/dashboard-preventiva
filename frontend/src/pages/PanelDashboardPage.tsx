import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import type {
  CatalogoFrontendResponseDto,
  DashboardPanelResponseDto,
  PanelMetricaResponseDto,
  TipoVisualizacion,
} from '../api/types'
import { ejecutarDashboard } from '../api/dashboardApi'
import { actualizarWidget, listarWidgets, obtenerDashboardMetadata } from '../api/panelesApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { DashboardFilters, FILTROS_VACIOS, aRequest } from '../components/dashboard/DashboardFilters'
import type { ValoresFiltros } from '../components/dashboard/DashboardFilters'
import { DashboardWidgetRenderer } from '../components/dashboard/DashboardWidgetRenderer'
import { DashboardHeader } from '../components/dashboard/DashboardHeader'
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

  // Definición "en crudo" de cada widget (metricaId, título/descripción
  // personalizados, orden, ancho): la necesitamos completa para poder hacer
  // PUT al cambiar solo tipoVisualizacion, porque el backend espera el objeto
  // entero (ver PanelMetricaServiceImpl.actualizar) y DashboardWidgetDto solo
  // trae los valores ya resueltos para pintar, no los campos en crudo.
  const [widgetsRaw, setWidgetsRaw] = useState<PanelMetricaResponseDto[]>([])

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
    listarWidgets(panelId ?? '', controller.signal)
      .then(setWidgetsRaw)
      .catch(() => {
        // Si falla, el selector de vista simplemente no se ofrece (ver
        // DashboardWidgetRenderer: sin onCambiarVisualizacion no hay riesgo,
        // solo se pierde la posibilidad de cambiar la vista hasta recargar).
      })
    return () => controller.abort()
  }, [panelId])

  const limpiar = () => {
    setFiltros(FILTROS_VACIOS)
    cargarDashboard(FILTROS_VACIOS, false)
  }

  // Cambia solo tipoVisualizacion de un widget, conservando el resto de su
  // configuración (metricaId, título/descripción, orden, ancho) tal cual
  // estaba. No crea ni duplica nada: reutiliza el mismo panelMetricaId.
  const cambiarVisualizacionWidget = async (panelMetricaId: number, nuevoTipo: TipoVisualizacion) => {
    const actual = widgetsRaw.find((w) => w.id === panelMetricaId)
    if (!actual) {
      throw new Error('No se encontró la configuración de este widget. Recarga la página e inténtalo de nuevo.')
    }
    const actualizado = await actualizarWidget(panelId ?? '', panelMetricaId, {
      metricaId: actual.metricaId,
      tituloPersonalizado: actual.tituloPersonalizado,
      descripcionPersonalizada: actual.descripcionPersonalizada,
      tipoVisualizacion: nuevoTipo,
      orden: actual.orden,
      ancho: actual.ancho,
    })
    setWidgetsRaw((filas) => filas.map((w) => (w.id === panelMetricaId ? actualizado : w)))
    await cargarDashboard(filtros, false)
  }

  return (
    <div className={styles.page}>
      <StateContainer loading={cargandoInicial} error={errorCarga && !datos ? errorCarga : null} empty={datos === null}>
        {datos && (
          <>
            <div className={styles.breadcrumbWrap}>
              <Breadcrumbs
                items={[
                  { label: 'Datasets', to: '/datasets' },
                  { label: datos.dataset.codigo, to: `/datasets/${datos.dataset.id}` },
                  { label: 'Paneles', to: `/datasets/${datos.dataset.id}/paneles` },
                  { label: `Dashboard de ${datos.panel.codigo}` },
                ]}
              />
            </div>

            <DashboardHeader
              panelNombre={datos.panel.nombre}
              panelCodigo={datos.panel.codigo}
              datasetNombre={datos.dataset.nombre}
              datasetId={datos.dataset.id}
              totalWidgets={datos.resumen.totalWidgets}
              widgetsOk={datos.resumen.widgetsOk}
              widgetsConError={datos.resumen.widgetsConError}
              esDashboardInicial={esDashboardInicial}
              configurarWidgetsHref={`/datasets/${datos.dataset.id}/paneles/${datos.panel.id}/widgets`}
            />

            <Card title="Periodo y granularidad" className={styles.filtrosCard}>
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
                  <DashboardWidgetRenderer
                    key={widget.panelMetricaId}
                    widget={widget}
                    onCambiarVisualizacion={cambiarVisualizacionWidget}
                  />
                ))}
              </div>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
