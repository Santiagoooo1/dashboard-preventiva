import { Link, useParams } from 'react-router'
import { useApiResource } from '../hooks/useApiResource'
import { obtenerFrontendMetadata } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import styles from './DatasetDetailPage.module.css'

export function DatasetDetailPage() {
  const { datasetId } = useParams<{ datasetId: string }>()
  const { data, loading, error } = useApiResource(
    (signal) => obtenerFrontendMetadata(datasetId ?? '', signal),
    [datasetId],
  )

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <h1>{data.dataset.nombre}</h1>
            <p className={styles.codigo}>{data.dataset.codigo}</p>
            {data.dataset.descripcion && <p>{data.dataset.descripcion}</p>}

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
              {data.metricas.length === 0 ? (
                <p>No hay métricas configuradas.</p>
              ) : (
                <DataTable
                  columns={[
                    { key: 'codigo', header: 'Código' },
                    { key: 'nombre', header: 'Nombre' },
                    { key: 'tipoMetrica', header: 'Tipo' },
                    { key: 'unidad', header: 'Unidad', render: (m) => m.unidad ?? '—' },
                  ]}
                  rows={data.metricas}
                  getRowKey={(m) => m.id}
                />
              )}
            </Card>

            <Card title="Paneles">
              {data.paneles.length === 0 ? (
                <p>No hay paneles configurados.</p>
              ) : (
                <ul className={styles.panelList}>
                  {data.paneles.map((panel) => (
                    <li key={panel.id}>
                      <Link to={`/paneles/${panel.id}/dashboard`}>
                        {panel.nombre} ({panel.codigo}) →
                      </Link>
                    </li>
                  ))}
                </ul>
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
