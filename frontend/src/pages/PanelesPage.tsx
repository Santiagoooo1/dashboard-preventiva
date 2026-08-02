import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { obtenerDataset } from '../api/datasetApi'
import { listarPaneles } from '../api/panelesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { PanelRowActions } from '../components/paneles/PanelRowActions'
import { DashboardInicialCard } from '../components/dashboard/DashboardInicialCard'
import styles from './PanelesPage.module.css'

export function PanelesPage() {
  const { datasetId } = useParams<{ datasetId: string }>()
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  const { data, loading, error, reload } = useApiResource(
    async (signal) => {
      const [dataset, paneles] = await Promise.all([
        obtenerDataset(datasetId ?? '', signal),
        listarPaneles(datasetId ?? '', signal),
      ])
      return { dataset, paneles }
    },
    [datasetId],
  )

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Paneles' },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>Paneles de {data.dataset.nombre}</h1>
              <Link className="btn btnPrimary" to={`/datasets/${datasetId}/paneles/nuevo`}>
                + Nuevo panel
              </Link>
            </div>

            <ErrorBanner mensaje={errorAccion} />

            {data.paneles.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  Este dataset no tiene paneles activos. Crea el primero para agrupar métricas en un dashboard.
                </p>
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/paneles/nuevo`}>
                  Nuevo panel
                </Link>
                <p className={styles.opcionAutomatica}>
                  Puedes generar automáticamente un primer panel con métricas básicas.
                </p>
                <DashboardInicialCard datasetId={Number(datasetId)} />
              </div>
            ) : (
              <Card title="Paneles">
                <DataTable
                  columns={[
                    { key: 'codigo', header: 'Código' },
                    { key: 'nombre', header: 'Nombre' },
                    { key: 'descripcion', header: 'Descripción', render: (p) => p.descripcion ?? '—' },
                    { key: 'orden', header: 'Orden' },
                    { key: 'activo', header: 'Activo', render: (p) => (p.activo ? 'Sí' : 'No') },
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
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
