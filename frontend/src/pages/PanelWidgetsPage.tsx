import { useEffect, useRef, useState } from 'react'
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
import { WidgetEditarForm } from '../components/paneles/WidgetEditarForm'
import { WidgetRowActions } from '../components/paneles/WidgetRowActions'
import {
  ETIQUETA_VISUALIZACION_OFRECIDA,
  normalizarTipoVisualizacion,
} from '../components/dashboard/visualizacionesCompatibles'
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

type FormAbierto =
  | { tipo: 'nuevo' }
  | { tipo: 'editar'; panelMetricaId: number; enfocarAgrupacion: boolean }
  | null

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
  const [mensajeExito, setMensajeExito] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  // El formulario se renderiza ARRIBA de la tabla, pero con catorce widgets el
  // usuario puede estar a mitad de página: se lleva la vista hasta él. Antes se
  // pintaba DEBAJO de la tabla, así que pulsar el botón parecía no hacer nada.
  const formularioRef = useRef<HTMLDivElement>(null)

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

  useEffect(() => {
    if (formAbierto) formularioRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [formAbierto])

  const cerrarForm = () => setFormAbierto(null)

  const trasGuardar = (mensaje: string) => {
    setFormAbierto(null)
    setGuardando(false)
    setMensajeExito(mensaje)
    reload()
  }

  const enError = (err: unknown, porDefecto: string) => {
    setErrorAccion(err instanceof Error ? err.message : porDefecto)
    setGuardando(false)
  }

  const onAnadir = async (payload: PanelMetricaRequestDto) => {
    setErrorAccion(null)
    setMensajeExito(null)
    setGuardando(true)
    try {
      await anadirWidget(panelId ?? '', payload)
      trasGuardar('Widget añadido al dashboard.')
    } catch (err) {
      enError(err, 'No se pudo añadir el widget.')
    }
  }

  /**
   * Guarda presentación y forma del resultado con una sola acción del usuario.
   * Son dos endpoints distintos, pero un único «Guardar cambios».
   */
  const onEditar = async (
    panelMetricaId: number,
    presentacion: PanelMetricaRequestDto,
    resultado: PanelMetricaConfiguracionWidgetRequestDto | null,
  ) => {
    setErrorAccion(null)
    setMensajeExito(null)
    setGuardando(true)
    try {
      await actualizarWidget(panelId ?? '', panelMetricaId, presentacion)
      if (resultado) {
        await actualizarConfiguracionWidget(panelId ?? '', panelMetricaId, resultado)
      }
      trasGuardar('Widget actualizado correctamente.')
    } catch (err) {
      enError(err, 'No se pudo actualizar el widget.')
    }
  }

  const metaDe = (panelMetricaId: number): WidgetMetadataDto | undefined =>
    data?.metadata.widgets.find((m) => m.panelMetricaId === panelMetricaId)

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
                { label: `Indicadores de ${data.metadata.panel.codigo}` },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>Indicadores y gráficos de «{data.metadata.panel.nombre}»</h1>
              <div className={styles.botonesCabecera}>
                <button
                  type="button"
                  className="btn btnPrimary"
                  onClick={() => setFormAbierto({ tipo: 'nuevo' })}
                >
                  + Añadir indicador o gráfico
                </button>
                {/* El panelId viaja como contexto de navegación (no se guarda en
                    la métrica): al terminar, el constructor propone volver a
                    ESTE dashboard sin que haya que elegirlo otra vez. */}
                <Link
                  className="btn btnSecondary"
                  to={`/datasets/${datasetId}/metricas/nueva/desde-columna?panelId=${panelId}`}
                >
                  + Crear indicador
                </Link>
                <Link className="btn btnSecondary" to={`/paneles/${panelId}/dashboard`}>
                  Ver dashboard
                </Link>
              </div>
            </div>
            <p className={styles.explicacion}>
              Aquí decides qué indicadores aparecen en este dashboard, cómo se ve cada uno (número,
              gráfico o tabla) y si está agrupado o segmentado. Para cambios rápidos sin entrar aquí, usa
              el menú de cada tarjeta directamente en el dashboard.
            </p>
            <p className={styles.subtitulo}>
              Dataset: {data.metadata.dataset.nombre} ({data.metadata.dataset.codigo})
            </p>

            <ErrorBanner mensaje={errorAccion} />
            {mensajeExito && <p className={styles.exito}>{mensajeExito}</p>}

            {/* Los formularios van ARRIBA de la tabla: son la respuesta visible
                a haber pulsado un botón. */}
            <div ref={formularioRef}>
              {formAbierto?.tipo === 'nuevo' && (
                <Card title="Añadir indicador o gráfico">
                  <WidgetForm
                    valorInicial={VALORES_NUEVO}
                    metricasDisponibles={data.metricas}
                    tipoVisualizaciones={data.catalogo.tipoVisualizaciones}
                    datasetId={datasetId ?? ''}
                    panelId={panelId ?? ''}
                    siguienteOrden={Math.max(0, ...data.widgets.map((w) => w.orden ?? 0)) + 1}
                    esEdicion={false}
                    onSubmit={onAnadir}
                    onCancelar={cerrarForm}
                    guardando={guardando}
                  />
                </Card>
              )}

              {formAbierto?.tipo === 'editar' &&
                (() => {
                  const widget: PanelMetricaResponseDto | undefined = data.widgets.find(
                    (w) => w.id === formAbierto.panelMetricaId,
                  )
                  if (!widget) return null
                  return (
                    <Card title={`Editar widget: ${widget.tituloPersonalizado ?? widget.metricaNombre}`}>
                      <WidgetEditarForm
                        widget={widget}
                        meta={metaDe(widget.id)}
                        tipoVisualizaciones={data.catalogo.tipoVisualizaciones}
                        camposFechaPermitidos={data.metadata.camposFechaPermitidos}
                        camposAgrupacionPermitidos={data.metadata.camposAgrupacionPermitidos}
                        granularidades={data.catalogo.granularidades}
                        enfocarAgrupacion={formAbierto.enfocarAgrupacion}
                        onGuardar={(presentacion, resultado) => onEditar(widget.id, presentacion, resultado)}
                        onCancelar={cerrarForm}
                        guardando={guardando}
                      />
                    </Card>
                  )
                })()}
            </div>

            {data.widgets.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  Este dashboard no tiene indicadores todavía. Añade el primero para ver resultados.
                </p>
                {formAbierto?.tipo !== 'nuevo' && (
                  <button type="button" className="btn btnPrimary" onClick={() => setFormAbierto({ tipo: 'nuevo' })}>
                    Añadir indicador o gráfico
                  </button>
                )}
              </div>
            ) : (
              <Card title="Indicadores y gráficos">
                <DataTable
                  columns={[
                    {
                      key: 'metrica',
                      header: 'Indicador',
                      render: (w) => w.tituloPersonalizado ?? w.metricaNombre,
                    },
                    {
                      key: 'vista',
                      header: 'Se ve como',
                      render: (w) => ETIQUETA_VISUALIZACION_OFRECIDA[normalizarTipoVisualizacion(w.tipoVisualizacion)],
                    },
                    {
                      key: 'calculo',
                      header: 'Cómo se calcula',
                      render: (w) => describirCalculo(metaDe(w.id)),
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
                          admiteAgrupar={(metaDe(w.id)?.tipoResultadosPermitidos.length ?? 1) > 1}
                          onEditar={() =>
                            setFormAbierto({ tipo: 'editar', panelMetricaId: w.id, enfocarAgrupacion: false })
                          }
                          onAgrupar={() =>
                            setFormAbierto({ tipo: 'editar', panelMetricaId: w.id, enfocarAgrupacion: true })
                          }
                          onError={setErrorAccion}
                          onQuitado={() => {
                            setMensajeExito('Widget quitado del dashboard.')
                            reload()
                          }}
                        />
                      ),
                    },
                  ]}
                  rows={data.widgets}
                  getRowKey={(w) => w.id}
                />
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
