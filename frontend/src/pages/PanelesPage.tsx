import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { obtenerDataset } from '../api/datasetApi'
import { listarPaneles } from '../api/panelesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { PanelRowActions } from '../components/paneles/PanelRowActions'
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
            <div className={styles.cabecera}>
              <h1>Paneles de {data.dataset.nombre}</h1>
              <Link className={styles.nuevo} to={`/datasets/${datasetId}/paneles/nuevo`}>
                + Nuevo panel
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

            {data.paneles.length === 0 ? (
              <p>Este dataset no tiene paneles activos.</p>
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
