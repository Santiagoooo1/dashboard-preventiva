import { Link } from 'react-router'
import { useApiResource } from '../hooks/useApiResource'
import { listarDatasets } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import styles from './DatasetsPage.module.css'

export function DatasetsPage() {
  const { data, loading, error } = useApiResource((signal) => listarDatasets(signal), [])

  return (
    <div className={styles.page}>
      <h1>Datasets clínicos</h1>
      <StateContainer
        loading={loading}
        error={error}
        empty={data !== null && data.length === 0}
        emptyMessage="No hay datasets activos."
      >
        <div className={styles.grid}>
          {data?.map((dataset) => (
            <Card key={dataset.id} title={dataset.nombre} subtitle={dataset.codigo}>
              {dataset.descripcion && <p>{dataset.descripcion}</p>}
              <Link to={`/datasets/${dataset.id}`}>Ver detalle →</Link>
            </Card>
          ))}
        </div>
      </StateContainer>
    </div>
  )
}
