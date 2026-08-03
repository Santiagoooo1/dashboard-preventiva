import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import type {
  CatalogoFrontendResponseDto,
  DashboardPanelResponseDto,
  DashboardWidgetDto,
  PanelMetricaResponseDto,
  TipoVisualizacion,
} from '../api/types'
import { ejecutarDashboard } from '../api/dashboardApi'
import { actualizarWidget, listarWidgets, obtenerDashboardMetadata } from '../api/panelesApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import { listarValoresUnicosDeCampo, obtenerFrontendMetadata } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { DashboardFilters, FILTROS_VACIOS, aRequest } from '../components/dashboard/DashboardFilters'
import type { ValoresFiltros } from '../components/dashboard/DashboardFilters'
import { clasificarCampos } from '../components/dashboard/camposFiltroDashboard'
import type { CampoFiltroCategoria, CamposClasificados } from '../components/dashboard/camposFiltroDashboard'
import { DashboardWidgetRenderer } from '../components/dashboard/DashboardWidgetRenderer'
import { DashboardHeader } from '../components/dashboard/DashboardHeader'
import styles from './PanelDashboardPage.module.css'

const CAMPOS_FECHA_O_NUMERICOS_EXCLUIDOS = new Set(['FECHA', 'ENTERO', 'DECIMAL'])
const SIN_CAMPOS: CamposClasificados = { principales: [], avanzados: [] }

/** Un widget se considera "sin resultados" si, con los filtros activos, no aporta ningún dato. */
function widgetSinResultados(widget: DashboardWidgetDto): boolean {
  if (widget.estado !== 'OK') return false

  if (widget.resultadoActual) {
    const r = widget.resultadoActual
    const sinItems = !r.items || r.items.length === 0
    const sinTotales = !r.totalDenominador && !r.totalNumerador
    return sinItems && sinTotales && (r.valor === null || r.valor === 0)
  }

  if (widget.comparativa) {
    return widget.comparativa.items.length === 0
  }

  if (widget.serieTemporal) {
    const s = widget.serieTemporal
    if (s.series) return s.series.every((serie) => serie.puntos.every((p) => !p.valor))
    if (s.puntos) return s.puntos.every((p) => !p.valor)
  }

  return false
}

function hayFiltrosActivos(valores: ValoresFiltros): boolean {
  return (
    Boolean(valores.paciente.trim()) || Object.values(valores.camposCategoria).some((v) => Boolean(v))
  )
}

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

  const [tienePaciente, setTienePaciente] = useState(false)
  const [valoresPaciente, setValoresPaciente] = useState<string[]>([])
  const [campos, setCampos] = useState<CamposClasificados>(SIN_CAMPOS)
  const [errorCamposCategoria, setErrorCamposCategoria] = useState<string | null>(null)

  // `filtros` es lo que el usuario está editando en el formulario;
  // `filtrosAplicados` es lo que realmente se envió al backend y por tanto lo
  // que describe el dashboard que se está viendo. Separarlos evita presentar
  // como "activo" un valor que el usuario aún no ha aplicado.
  const [filtros, setFiltros] = useState<ValoresFiltros>(FILTROS_VACIOS)
  const [filtrosAplicados, setFiltrosAplicados] = useState<ValoresFiltros>(FILTROS_VACIOS)

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
        setFiltrosAplicados(valores)
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
      .then(async (m) => {
        setCamposFecha(m.camposFechaPermitidos)

        try {
          const datasetId = m.dataset.id
          const fm = await obtenerFrontendMetadata(datasetId, controller.signal)

          const categoricos = fm.campos.filter(
            (c) =>
              c.activo &&
              c.roles.filtrable &&
              c.codigo !== 'pacienteCodigo' &&
              !CAMPOS_FECHA_O_NUMERICOS_EXCLUIDOS.has(c.tipoDato),
          )
          const conValores: CampoFiltroCategoria[] = await Promise.all(
            categoricos.map(async (c) => ({
              codigo: c.codigo,
              etiqueta: c.etiqueta,
              tipoDato: c.tipoDato,
              valores: await listarValoresUnicosDeCampo(datasetId, c.codigo, controller.signal),
            })),
          )
          // Solo se ofrecen campos que realmente tengan valores en los datos:
          // un selector vacío no aporta nada y suma ruido.
          setCampos(clasificarCampos(conValores.filter((c) => c.valores.length > 0)))

          const paciente = fm.campos.find((c) => c.codigo === 'pacienteCodigo' && c.activo && c.roles.filtrable)
          if (paciente) {
            setTienePaciente(true)
            setValoresPaciente(await listarValoresUnicosDeCampo(datasetId, 'pacienteCodigo', controller.signal))
          }
        } catch (err) {
          if (!controller.signal.aborted) {
            setErrorCamposCategoria(err instanceof Error ? err.message : 'error desconocido')
          }
        }
      })
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

  // Aplica un conjunto concreto de filtros al instante (quitar una chip, quitar
  // el paciente), sin esperar a que el usuario pulse "Aplicar filtros".
  const aplicarValores = (nuevosValores: ValoresFiltros) => {
    setFiltros(nuevosValores)
    cargarDashboard(nuevosValores, false)
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

            <Card title="Filtrar el análisis" className={styles.filtrosCard}>
              <DashboardFilters
                valores={filtros}
                aplicados={filtrosAplicados}
                onChange={setFiltros}
                onAplicar={() => cargarDashboard(filtros, false)}
                onLimpiar={limpiar}
                onAplicarValores={aplicarValores}
                granularidades={catalogo?.granularidades ?? []}
                camposFechaPermitidos={camposFecha}
                errorMetadata={errorMetadata}
                cargando={aplicando}
                tienePaciente={tienePaciente}
                valoresPaciente={valoresPaciente}
                camposPrincipales={campos.principales}
                camposAvanzados={campos.avanzados}
                errorCamposCategoria={errorCamposCategoria}
              />
            </Card>

            {errorCarga && <ErrorBanner mensaje={errorCarga} />}

            {datos.widgets.length > 0 &&
              !aplicando &&
              hayFiltrosActivos(filtrosAplicados) &&
              datos.widgets.every(widgetSinResultados) && (
                <div className={styles.sinResultados}>
                  <h2 className={styles.sinResultadosTitulo}>
                    No hay registros para los filtros seleccionados.
                  </h2>
                  <p className={styles.sinResultadosTexto}>
                    Prueba a quitar algún filtro o restablecer el análisis completo.
                  </p>
                  <button type="button" className="btn btnPrimary" onClick={limpiar}>
                    Limpiar filtros
                  </button>
                </div>
              )}

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
