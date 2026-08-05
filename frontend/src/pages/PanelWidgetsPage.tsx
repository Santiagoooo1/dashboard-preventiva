import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type {
  PanelMetricaConfiguracionWidgetRequestDto,
  PanelMetricaRequestDto,
  PanelMetricaResponseDto,
  WidgetMetadataDto,
} from '../api/types'
import {
  actualizarConfiguracionWidget,
  actualizarWidget,
  anadirWidget,
  listarWidgets,
  obtenerDashboardMetadata,
} from '../api/panelesApi'
import { listarMetricas } from '../api/metricasApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { WidgetForm } from '../components/paneles/WidgetForm'
import type { WidgetFormValores } from '../components/paneles/WidgetForm'
import { WidgetConfigForm } from '../components/paneles/WidgetConfigForm'
import { WidgetRowActions } from '../components/paneles/WidgetRowActions'
import { ETIQUETA_VISUALIZACION_OFRECIDA, normalizarTipoVisualizacion } from '../components/dashboard/visualizacionesCompatibles'
import styles from './PanelWidgetsPage.module.css'

const ANCHO_ETIQUETA: Record<number, string> = { 3: 'Pequeño', 6: 'Medio', 12: 'Ancho completo' }

/** "Cómo se calcula" este widget, en lenguaje de usuario — nunca el enum crudo. */
function describirCalculo(meta: WidgetMetadataDto | undefined): string {
  if (!meta) return '—'
  const config = meta.configuracionWidgetActual
  if (meta.tipoResultadoActual === 'COMPARATIVA' && config?.campoAgrupacion) {
    return `Agrupado por ${config.campoAgrupacion}`
  }
  if (meta.tipoResultadoActual === 'SERIE_TEMPORAL') {
    return config?.campoSegmentacion
      ? `Evolución en el tiempo, por ${config.campoSegmentacion}`
      : 'Evolución en el tiempo'
  }
  return 'Valor único'
}

type FormAbierto = { tipo: 'nuevo' } | { tipo: 'editar' | 'config'; panelMetricaId: number } | null

const VALORES_NUEVO: WidgetFormValores = {
  metricaId: '',
  tituloPersonalizado: '',
  descripcionPersonalizada: '',
  tipoVisualizacion: '',
  orden: '',
  ancho: '',
}

export function PanelWidgetsPage() {
  const { datasetId, panelId } = useParams<{ datasetId: string; panelId: string }>()
  const [formAbierto, setFormAbierto] = useState<FormAbierto>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const { data, loading, error, reload } = useApiResource(
    async (signal) => {
      const [metadata, catalogo, widgets, metricas] = await Promise.all([
        obtenerDashboardMetadata(panelId ?? '', signal),
        getCatalogo(signal),
        listarWidgets(panelId ?? '', signal),
        listarMetricas(datasetId ?? '', signal),
      ])
      return { metadata, catalogo, widgets, metricas }
    },
    [datasetId, panelId],
  )

  const cerrarForm = () => setFormAbierto(null)

  const trasGuardar = () => {
    setFormAbierto(null)
    setGuardando(false)
    reload()
  }

  const enError = (err: unknown, porDefecto: string) => {
    setErrorAccion(err instanceof Error ? err.message : porDefecto)
    setGuardando(false)
  }

  const onAnadir = async (payload: PanelMetricaRequestDto) => {
    setErrorAccion(null)
    setGuardando(true)
    try {
      await anadirWidget(panelId ?? '', payload)
      trasGuardar()
    } catch (err) {
      enError(err, 'Error al añadir el widget.')
    }
  }

  const onEditar = async (panelMetricaId: number, payload: PanelMetricaRequestDto) => {
    setErrorAccion(null)
    setGuardando(true)
    try {
      await actualizarWidget(panelId ?? '', panelMetricaId, payload)
      trasGuardar()
    } catch (err) {
      enError(err, 'Error al actualizar el widget.')
    }
  }

  const onConfigurar = async (
    panelMetricaId: number,
    payload: PanelMetricaConfiguracionWidgetRequestDto,
  ) => {
    setErrorAccion(null)
    setGuardando(true)
    try {
      await actualizarConfiguracionWidget(panelId ?? '', panelMetricaId, payload)
      trasGuardar()
    } catch (err) {
      enError(err, 'Error al configurar el widget.')
    }
  }

  const valoresEdicion = (w: PanelMetricaResponseDto): WidgetFormValores => ({
    metricaId: String(w.metricaId),
    tituloPersonalizado: w.tituloPersonalizado ?? '',
    descripcionPersonalizada: w.descripcionPersonalizada ?? '',
    tipoVisualizacion: w.tipoVisualizacion,
    orden: String(w.orden ?? ''),
    ancho: String(w.ancho ?? ''),
  })

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.metadata.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Paneles', to: `/datasets/${datasetId}/paneles` },
                { label: `Widgets de ${data.metadata.panel.codigo}` },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>Cómo se visualiza cada métrica en «{data.metadata.panel.nombre}»</h1>
              <div className={styles.botonesCabecera}>
                <button
                  type="button"
                  className="btn btnPrimary"
                  onClick={() => setFormAbierto({ tipo: 'nuevo' })}
                >
                  + Añadir widget
                </button>
                {/* Añadir widget usa una métrica que YA existe; esta otra crea
                    una nueva. Son dos acciones distintas y conviene que se vean
                    como tales, en vez de esconder la segunda tras la primera. */}
                <Link
                  className="btn btnSecondary"
                  to={`/datasets/${datasetId}/metricas/nueva/desde-columna`}
                >
                  + Crear métrica desde columna
                </Link>
                <Link className="btn btnSecondary" to={`/paneles/${panelId}/dashboard`}>
                  Ver dashboard
                </Link>
              </div>
            </div>
            <p className={styles.explicacion}>
              Aquí decides qué métricas aparecen en este panel, cómo se ve cada una (número, gráfico o
              tabla) y si está agrupada o segmentada. Para cambios rápidos de visualización sin entrar
              aquí, usa el selector "Vista" de cada widget directamente en el dashboard.
            </p>
            <p className={styles.subtitulo}>
              Dataset: {data.metadata.dataset.nombre} ({data.metadata.dataset.codigo})
            </p>

            <ErrorBanner mensaje={errorAccion} />

            {formAbierto?.tipo === 'nuevo' && (
              <Card title="Añadir widget">
                <WidgetForm
                  valorInicial={VALORES_NUEVO}
                  metricasDisponibles={data.metricas}
                  tipoVisualizaciones={data.catalogo.tipoVisualizaciones}
                  datasetId={datasetId ?? ''}
                  esEdicion={false}
                  onSubmit={onAnadir}
                  onCancelar={cerrarForm}
                  guardando={guardando}
                />
              </Card>
            )}

            {data.widgets.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  Este panel no tiene widgets. Añade el primero para que el dashboard muestre resultados.
                </p>
                {formAbierto?.tipo !== 'nuevo' && (
                  <button type="button" className="btn btnPrimary" onClick={() => setFormAbierto({ tipo: 'nuevo' })}>
                    Añadir widget
                  </button>
                )}
              </div>
            ) : (
              <Card title="Widgets del panel">
                <DataTable
                  columns={[
                    {
                      key: 'metrica',
                      header: 'Métrica',
                      render: (w) => w.tituloPersonalizado ?? w.metricaNombre,
                    },
                    {
                      key: 'vista',
                      header: 'Vista',
                      render: (w) => ETIQUETA_VISUALIZACION_OFRECIDA[normalizarTipoVisualizacion(w.tipoVisualizacion)],
                    },
                    {
                      key: 'calculo',
                      header: 'Cómo se calcula',
                      render: (w) => describirCalculo(data.metadata.widgets.find((m) => m.panelMetricaId === w.id)),
                    },
                    {
                      key: 'ancho',
                      header: 'Tamaño',
                      render: (w) => ANCHO_ETIQUETA[w.ancho] ?? `${w.ancho} columnas`,
                    },
                    {
                      key: 'acciones',
                      header: 'Acciones',
                      render: (w) => (
                        <WidgetRowActions
                          panelId={panelId ?? ''}
                          widget={w}
                          onEditar={() => setFormAbierto({ tipo: 'editar', panelMetricaId: w.id })}
                          onConfigurar={() => setFormAbierto({ tipo: 'config', panelMetricaId: w.id })}
                          onError={setErrorAccion}
                          onQuitado={reload}
                        />
                      ),
                    },
                  ]}
                  rows={data.widgets}
                  getRowKey={(w) => w.id}
                />
              </Card>
            )}

            {formAbierto?.tipo === 'editar' &&
              (() => {
                const widget = data.widgets.find((w) => w.id === formAbierto.panelMetricaId)
                if (!widget) return null
                return (
                  <Card title={`Editar widget: ${widget.tituloPersonalizado ?? widget.metricaNombre}`}>
                    <WidgetForm
                      valorInicial={valoresEdicion(widget)}
                      metricasDisponibles={data.metricas}
                      tipoVisualizaciones={data.catalogo.tipoVisualizaciones}
                      datasetId={datasetId ?? ''}
                      esEdicion
                      onSubmit={(payload) => onEditar(widget.id, payload)}
                      onCancelar={cerrarForm}
                      guardando={guardando}
                    />
                  </Card>
                )
              })()}

            {formAbierto?.tipo === 'config' &&
              (() => {
                const meta = data.metadata.widgets.find((m) => m.panelMetricaId === formAbierto.panelMetricaId)
                if (!meta) {
                  return (
                    <Card title="Configurar resultado">
                      <p>
                        No hay metadata disponible para este widget. Puede que su métrica esté desactivada.
                      </p>
                    </Card>
                  )
                }
                return (
                  <Card title={`Configurar resultado: ${meta.nombre}`}>
                    <WidgetConfigForm
                      widget={meta}
                      camposFechaPermitidos={data.metadata.camposFechaPermitidos}
                      camposAgrupacionPermitidos={data.metadata.camposAgrupacionPermitidos}
                      granularidades={data.catalogo.granularidades}
                      onSubmit={(payload) => onConfigurar(meta.panelMetricaId, payload)}
                      onCancelar={cerrarForm}
                      guardando={guardando}
                    />
                  </Card>
                )
              })()}
          </>
        )}
      </StateContainer>
    </div>
  )
}
