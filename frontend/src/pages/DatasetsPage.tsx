import { useState } from 'react'
import { Link } from 'react-router'
import { useApiResource } from '../hooks/useApiResource'
import { listarDatasets } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { ErrorBanner } from '../components/ErrorBanner'
import { DatasetRowActions } from '../components/datasets/DatasetRowActions'
import styles from './DatasetsPage.module.css'

export function DatasetsPage() {
  const { data, loading, error, reload } = useApiResource((signal) => listarDatasets(signal), [])
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  return (
    <div className={styles.page}>
      <div className={styles.cabecera}>
        <h1>Datasets clínicos</h1>
        <Link className="btn btnPrimary" to="/datasets/nuevo">
          Crear nuevo dataset
        </Link>
      </div>
      <p className={styles.intro}>
        Un dataset representa una fuente de datos clínicos sobre la que se definen campos, métricas y paneles.
      </p>

      <ErrorBanner mensaje={errorAccion} />

      <StateContainer
        loading={loading}
        error={error}
        empty={data !== null && data.length === 0}
        emptyMessage="No hay datasets todavía. Crea el primero para empezar a definir campos y métricas."
        emptyAction={
          <Link className="btn btnPrimary" to="/datasets/nuevo">
            Crear nuevo dataset
          </Link>
        }
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
