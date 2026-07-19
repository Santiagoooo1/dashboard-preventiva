import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type { MetricaClinicaResponseDto, ResultadoMetricaResponseDto } from '../api/types'
import { listarMetricas } from '../api/metricasApi'
import { obtenerFrontendMetadata } from '../api/datasetApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { MetricaRowActions } from '../components/metrics/MetricaRowActions'
import { WidgetActual } from '../components/widgets/WidgetActual'
import styles from './DatasetMetricasPage.module.css'

export function DatasetMetricasPage() {
  const { datasetId } = useParams<{ datasetId: string }>()

  const { data, loading, error, reload } = useApiResource(
    async (signal) => {
      const [metadata, metricas] = await Promise.all([
        obtenerFrontendMetadata(datasetId ?? '', signal),
        listarMetricas(datasetId ?? '', signal),
      ])
      return { dataset: metadata.dataset, metricas }
    },
    [datasetId],
  )

  const [ultimoResultado, setUltimoResultado] = useState<{
    metrica: MetricaClinicaResponseDto
    resultado: ResultadoMetricaResponseDto
  } | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  const onResultado = (metrica: MetricaClinicaResponseDto, resultado: ResultadoMetricaResponseDto) => {
    setErrorAccion(null)
    setUltimoResultado({ metrica, resultado })
  }

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <div className={styles.cabecera}>
              <h1>Métricas de {data.dataset.nombre}</h1>
              <Link className={styles.nueva} to={`/datasets/${datasetId}/metricas/nueva`}>
                + Nueva métrica
              </Link>
            </div>
            <p>
              <Link to={`/datasets/${datasetId}`}>← Volver al dataset</Link>
            </p>

            {errorAccion && (
              <div className={styles.bannerError} role="alert">
                {errorAccion}
              </div>
            )}

            {data.metricas.length === 0 ? (
              <p>No hay métricas configuradas en este dataset.</p>
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
