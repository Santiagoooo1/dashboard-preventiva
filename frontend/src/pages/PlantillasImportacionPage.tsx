import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { obtenerDataset } from '../api/datasetApi'
import { listarPlantillasImportacion } from '../api/plantillasImportacionApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { PlantillaRowActions } from '../components/importacion/PlantillaRowActions'
import styles from './PanelesPage.module.css'

export function PlantillasImportacionPage() {
  const { datasetId } = useParams<{ datasetId: string }>()
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  const { data, loading, error, reload } = useApiResource(
    async (signal) => {
      const [dataset, plantillas] = await Promise.all([
        obtenerDataset(datasetId ?? '', signal),
        listarPlantillasImportacion(datasetId ?? '', signal),
      ])
      return { dataset, plantillas }
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
                { label: 'Plantillas de importación' },
              ]}
            />
            <div className={styles.cabecera}>
              <h1>Plantillas de importación</h1>
              <Link className="btn btnPrimary" to={`/datasets/${datasetId}/plantillas/nueva`}>
                + Nueva plantilla
              </Link>
            </div>
            <p>
              Una plantilla describe cómo leer un archivo: su origen, la fila de cabecera y a qué campo clínico
              corresponde cada columna.
            </p>

            <ErrorBanner mensaje={errorAccion} />

            {data.plantillas.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  Este dataset no tiene plantillas de importación. Crea la primera para poder importar datos.
                </p>
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/plantillas/nueva`}>
                  Crear plantilla de importación
                </Link>
              </div>
            ) : (
              <Card title="Plantillas">
                <DataTable
                  columns={[
                    { key: 'nombre', header: 'Nombre' },
                    { key: 'origen', header: 'Origen', render: (p) => p.origen ?? '—' },
                    { key: 'filaCabecera', header: 'Fila cabecera', render: (p) => p.filaCabecera ?? '—' },
                    { key: 'activa', header: 'Activa', render: (p) => (p.activa ? 'Sí' : 'No') },
                    {
                      key: 'acciones',
                      header: 'Acciones',
                      render: (p) => (
                        <PlantillaRowActions
                          datasetId={datasetId ?? ''}
                          plantilla={p}
                          onError={setErrorAccion}
                          onEliminada={reload}
                        />
                      ),
                    },
                  ]}
                  rows={data.plantillas}
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
