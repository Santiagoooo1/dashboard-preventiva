import { useState } from 'react'
import { Link } from 'react-router'
import { useApiResource } from '../hooks/useApiResource'
import { listarDatasets } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { DatasetRowActions } from '../components/datasets/DatasetRowActions'
import styles from './DatasetsPage.module.css'

export function DatasetsPage() {
  const { data, loading, error, reload } = useApiResource((signal) => listarDatasets(signal), [])
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  return (
    <div className={styles.page}>
      <div className={styles.cabecera}>
        <h1>Datasets clínicos</h1>
        <Link className={styles.nuevo} to="/datasets/nuevo">
          + Nuevo dataset
        </Link>
      </div>

      {errorAccion && (
        <div className={styles.bannerError} role="alert">
          {errorAccion}
        </div>
      )}

      <StateContainer
        loading={loading}
        error={error}
        empty={data !== null && data.length === 0}
        emptyMessage="No hay datasets activos. Crea el primero con “Nuevo dataset”."
      >
        <Card title="Datasets">
          <DataTable
            columns={[
              { key: 'codigo', header: 'Código' },
              { key: 'nombre', header: 'Nombre' },
              { key: 'descripcion', header: 'Descripción', render: (d) => d.descripcion ?? '—' },
              { key: 'activo', header: 'Activo', render: (d) => (d.activo ? 'Sí' : 'No') },
              {
                key: 'acciones',
                header: 'Acciones',
                render: (d) => (
                  <DatasetRowActions dataset={d} onError={setErrorAccion} onEliminado={reload} />
                ),
              },
            ]}
            rows={data ?? []}
            getRowKey={(d) => d.id}
          />
        </Card>
      </StateContainer>
    </div>
  )
}
