import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type { MetricaClinicaResponseDto, ResultadoMetricaResponseDto } from '../api/types'
import { useApiResource } from '../hooks/useApiResource'
import { obtenerFrontendMetadata } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { MetricaRowActions } from '../components/metrics/MetricaRowActions'
import { PanelRowActions } from '../components/paneles/PanelRowActions'
import { WidgetActual } from '../components/widgets/WidgetActual'
import styles from './DatasetDetailPage.module.css'

export function DatasetDetailPage() {
  const { datasetId } = useParams<{ datasetId: string }>()
  const { data, loading, error, reload } = useApiResource(
    (signal) => obtenerFrontendMetadata(datasetId ?? '', signal),
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
            <h1>{data.dataset.nombre}</h1>
            <p className={styles.codigo}>{data.dataset.codigo}</p>
            {data.dataset.descripcion && <p>{data.dataset.descripcion}</p>}

            <div className={styles.accionesDataset}>
              <Link className={styles.accionDataset} to={`/datasets/${datasetId}/campos`}>
                Gestionar campos
              </Link>
              <Link className={styles.accionDataset} to={`/datasets/${datasetId}/metricas`}>
                Gestionar métricas
              </Link>
              <Link className={styles.accionDataset} to={`/datasets/${datasetId}/metricas/nueva`}>
                Nueva métrica
              </Link>
              <Link className={styles.accionDataset} to={`/datasets/${datasetId}/paneles`}>
                Gestionar paneles
              </Link>
              <Link className={styles.accionDataset} to={`/datasets/${datasetId}/editar`}>
                Editar dataset
              </Link>
            </div>

            <Card title="Resumen de configuración">
              <ul className={styles.resumenList}>
                <li>Campos: {data.resumenConfiguracion.totalCampos}</li>
                <li>Métricas: {data.resumenConfiguracion.totalMetricas}</li>
                <li>Paneles: {data.resumenConfiguracion.totalPaneles}</li>
                <li>Plantillas de importación: {data.resumenConfiguracion.totalPlantillasImportacion}</li>
              </ul>
            </Card>

            <Card title="Campos clínicos">
              {data.campos.length === 0 ? (
                <p>No hay campos clínicos configurados.</p>
              ) : (
                <DataTable
                  columns={[
                    { key: 'codigo', header: 'Código' },
                    { key: 'etiqueta', header: 'Etiqueta' },
                    { key: 'tipoDato', header: 'Tipo' },
                    { key: 'esComun', header: 'Común', render: (c) => (c.esComun ? 'Sí' : 'No') },
                    {
                      key: 'roles',
                      header: 'Roles',
                      render: (c) =>
                        [
                          c.roles.filtrable && 'filtrable',
                          c.roles.agrupable && 'agrupable',
                          c.roles.numerico && 'numérico',
                          c.roles.fecha && 'fecha',
                        ]
                          .filter(Boolean)
                          .join(', ') || '—',
                    },
                  ]}
                  rows={data.campos}
                  getRowKey={(c) => c.id}
                />
              )}
            </Card>

            <Card title="Métricas">
              <div className={styles.metricasAcciones}>
                <Link className={styles.botonNueva} to={`/datasets/${datasetId}/metricas/nueva`}>
                  + Nueva métrica
                </Link>
                <Link to={`/datasets/${datasetId}/metricas`}>Ver todas las métricas →</Link>
              </div>
              {errorAccion && (
                <p className="stateError" role="alert">
                  {errorAccion}
                </p>
              )}
              {data.metricas.length === 0 ? (
                <p>No hay métricas configuradas.</p>
              ) : (
                <DataTable
                  columns={[
                    { key: 'codigo', header: 'Código' },
                    { key: 'nombre', header: 'Nombre' },
                    { key: 'tipoMetrica', header: 'Tipo' },
                    { key: 'unidad', header: 'Unidad', render: (m) => m.unidad ?? '—' },
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
              )}
              {ultimoResultado && (
                <Card
                  title={`Resultado: ${ultimoResultado.metrica.nombre}`}
                  subtitle={ultimoResultado.metrica.tipoMetrica}
                >
                  <WidgetActual resultado={ultimoResultado.resultado} />
                </Card>
              )}
            </Card>

            <Card title="Paneles">
              <div className={styles.metricasAcciones}>
                <Link className={styles.botonNueva} to={`/datasets/${datasetId}/paneles/nuevo`}>
                  + Nuevo panel
                </Link>
                <Link to={`/datasets/${datasetId}/paneles`}>Ver todos los paneles →</Link>
              </div>
              {data.paneles.length === 0 ? (
                <p>No hay paneles configurados.</p>
              ) : (
                <DataTable
                  columns={[
                    { key: 'codigo', header: 'Código' },
                    { key: 'nombre', header: 'Nombre' },
                    { key: 'orden', header: 'Orden' },
                    {
                      key: 'acciones',
                      header: 'Acciones',
                      render: (p) => (
                        <PanelRowActions
                          datasetId={datasetId ?? ''}
                          panel={p}
                          onError={setErrorAccion}
                          onEliminado={reload}
                        />
                      ),
                    },
                  ]}
                  rows={data.paneles}
                  getRowKey={(p) => p.id}
                />
              )}
            </Card>

            <Card title="Plantillas de importación">
              {data.plantillasImportacion.length === 0 ? (
                <p>No hay plantillas de importación configuradas.</p>
              ) : (
                <DataTable
                  columns={[
                    { key: 'nombre', header: 'Nombre' },
                    { key: 'origen', header: 'Origen', render: (p) => p.origen ?? '—' },
                    { key: 'filaCabecera', header: 'Fila cabecera', render: (p) => p.filaCabecera ?? '—' },
                  ]}
                  rows={data.plantillasImportacion}
                  getRowKey={(p) => p.id}
                />
              )}
            </Card>
          </>
        )}
      </StateContainer>
    </div>
  )
}
