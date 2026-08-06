import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import type { MetricaClinicaResponseDto, ResultadoMetricaResponseDto } from '../api/types'
import { listarMetricas, obtenerMetadataMetricas } from '../api/metricasApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import { listarPaneles } from '../api/panelesApi'
import { obtenerFrontendMetadata } from '../api/datasetApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { MetricaRowActions } from '../components/metrics/MetricaRowActions'
import { MetricaCreadaPanel } from '../components/metrics/MetricaCreadaPanel'
import { WidgetActual } from '../components/widgets/WidgetActual'
import { DashboardInicialCard } from '../components/dashboard/DashboardInicialCard'
import styles from './DatasetMetricasPage.module.css'

export function DatasetMetricasPage() {
  const { datasetId } = useParams<{ datasetId: string }>()
  const [searchParams] = useSearchParams()

  const { data, loading, error, reload } = useApiResource(
    async (signal) => {
      const [metadata, metricas, metadataMetricas, paneles, catalogo] = await Promise.all([
        obtenerFrontendMetadata(datasetId ?? '', signal),
        listarMetricas(datasetId ?? '', signal),
        obtenerMetadataMetricas(datasetId ?? '', signal),
        listarPaneles(datasetId ?? '', signal),
        getCatalogo(signal),
      ])
      return { dataset: metadata.dataset, metricas, campos: metadataMetricas.campos, paneles, catalogo }
    },
    [datasetId],
  )

  const [ultimoResultado, setUltimoResultado] = useState<{
    metrica: MetricaClinicaResponseDto
    resultado: ResultadoMetricaResponseDto
  } | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  // Métrica que el usuario quiere llevar a un dashboard: reutiliza el mismo
  // formulario rápido que aparece justo después de crear una.
  const [metricaParaWidget, setMetricaParaWidget] = useState<MetricaClinicaResponseDto | null>(null)

  // ?anadir=<id> abre el formulario rápido directamente: es como llega el
  // usuario desde la vista avanzada del dataset.
  useEffect(() => {
    const id = searchParams.get('anadir')
    if (!id || !data) return
    const metrica = data.metricas.find((m) => String(m.id) === id)
    if (metrica) setMetricaParaWidget(metrica)
  }, [searchParams, data])

  const onResultado = (metrica: MetricaClinicaResponseDto, resultado: ResultadoMetricaResponseDto) => {
    setErrorAccion(null)
    setUltimoResultado({ metrica, resultado })
  }

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Métricas' },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>Métricas de {data.dataset.nombre}</h1>
              <div className={styles.botonesCabecera}>
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/metricas/nueva/desde-columna`}>
                  + Crear métrica desde columna
                </Link>
                <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas/nueva`}>
                  Otras formas de crear
                </Link>
              </div>
            </div>

            <ErrorBanner mensaje={errorAccion} />

            {metricaParaWidget && data.campos && (
              <MetricaCreadaPanel
                datasetId={datasetId ?? ''}
                metrica={metricaParaWidget}
                campos={data.campos}
                paneles={data.paneles}
                granularidades={data.catalogo.granularidades}
                abrirFormulario
                onCrearOtra={() => setMetricaParaWidget(null)}
                rutaCatalogo={`/datasets/${datasetId}/metricas`}
              />
            )}

            {data.metricas.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  No hay métricas configuradas en este dataset. Crea la primera para empezar a medir.
                </p>
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/metricas/nueva/desde-columna`}>
                  Crear métrica desde columna
                </Link>
                <p className={styles.opcionAutomatica}>
                  También puedes generar métricas iniciales automáticamente a partir de los campos importados.
                </p>
                <DashboardInicialCard datasetId={Number(datasetId)} />
              </div>
            ) : (
              <Card title="Métricas">
                <DataTable
                  columns={[
                    { key: 'codigo', header: 'Código' },
                    { key: 'nombre', header: 'Nombre' },
                    { key: 'tipoMetrica', header: 'Tipo' },
                    { key: 'unidad', header: 'Unidad', render: (m) => m.unidad ?? '—' },
                    { key: 'orden', header: 'Orden' },
                    { key: 'activa', header: 'Activa', render: (m) => (m.activa ? 'Sí' : 'No') },
                    {
                      key: 'acciones',
                      header: 'Acciones',
                      render: (m) => (
                        <MetricaRowActions
                          metrica={m}
                          onResultado={onResultado}
                          onError={setErrorAccion}
                          onDesactivada={reload}
                          onAnadirADashboard={(m) => {
                            setErrorAccion(null)
                            setMetricaParaWidget(m)
                            window.scrollTo({ top: 0, behavior: 'smooth' })
                          }}
                        />
                      ),
                    },
                  ]}
                  rows={data.metricas}
                  getRowKey={(m) => m.id}
                />
              </Card>
            )}

            {ultimoResultado && (
              <Card
                title={`Resultado: ${ultimoResultado.metrica.nombre}`}
                subtitle={ultimoResultado.metrica.tipoMetrica}
              >
                <WidgetActual resultado={ultimoResultado.resultado} />
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
