import { useApiResource } from '../hooks/useApiResource'
import { getCatalogo } from '../api/frontendCatalogApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import styles from './CatalogoPage.module.css'

export function CatalogoPage() {
  const { data, loading, error } = useApiResource((signal) => getCatalogo(signal), [])

  return (
    <div className={styles.page}>
      <h1>Catálogo técnico</h1>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Card title="Tipos de métrica">
              <DataTable
                columns={[
                  { key: 'codigo', header: 'Código' },
                  { key: 'nombre', header: 'Nombre' },
                  {
                    key: 'requiereCampoValor',
                    header: 'Campo valor',
                    render: (t) => (t.requiereCampoValor ? 'Sí' : 'No'),
                  },
                  {
                    key: 'requiereCampoAgrupacion',
                    header: 'Campo agrupación',
                    render: (t) => (t.requiereCampoAgrupacion ? 'Sí' : 'No'),
                  },
                  {
                    key: 'permiteSerieTemporal',
                    header: 'Serie temporal',
                    render: (t) => (t.permiteSerieTemporal ? 'Sí' : 'No'),
                  },
                  {
                    key: 'permiteComparativa',
                    header: 'Comparativa',
                    render: (t) => (t.permiteComparativa ? 'Sí' : 'No'),
                  },
                ]}
                rows={data.tipoMetricas}
                getRowKey={(t) => t.codigo}
              />
            </Card>

            <Card title="Operadores de filtro">
              <DataTable
                columns={[
                  { key: 'codigo', header: 'Código' },
                  { key: 'nombre', header: 'Nombre' },
                  {
                    key: 'tiposDatoCompatibles',
                    header: 'Tipos compatibles',
                    render: (o) => o.tiposDatoCompatibles.join(', '),
                  },
                  { key: 'requiereValor', header: 'Requiere valor', render: (o) => (o.requiereValor ? 'Sí' : 'No') },
                  { key: 'requiereLista', header: 'Requiere lista', render: (o) => (o.requiereLista ? 'Sí' : 'No') },
                ]}
                rows={data.operadoresFiltro}
                getRowKey={(o) => o.codigo}
              />
            </Card>

            <Card title="Tipos de visualización">
              <DataTable
                columns={[
                  { key: 'codigo', header: 'Código' },
                  { key: 'nombre', header: 'Nombre' },
                  {
                    key: 'tipoResultadoPorDefecto',
                    header: 'Resultado por defecto',
                    render: (v) => v.tipoResultadoPorDefecto ?? 'Depende de la configuración',
                  },
                ]}
                rows={data.tipoVisualizaciones}
                getRowKey={(v) => v.codigo}
              />
            </Card>

            <Card title="Tipos de resultado de widget">
              <DataTable
                columns={[
                  { key: 'codigo', header: 'Código' },
                  { key: 'nombre', header: 'Nombre' },
                  { key: 'descripcion', header: 'Descripción', render: (o) => o.descripcion ?? '—' },
                ]}
                rows={data.tipoResultadoWidget}
                getRowKey={(o) => o.codigo}
              />
            </Card>

            <Card title="Granularidades">
              <DataTable
                columns={[
                  { key: 'codigo', header: 'Código' },
                  { key: 'nombre', header: 'Nombre' },
                ]}
                rows={data.granularidades}
                getRowKey={(o) => o.codigo}
              />
            </Card>

            <Card title="Tipos de dato">
              <DataTable
                columns={[
                  { key: 'codigo', header: 'Código' },
                  { key: 'nombre', header: 'Nombre' },
                ]}
                rows={data.tiposDato}
                getRowKey={(o) => o.codigo}
              />
            </Card>

            <Card title="Políticas de campo faltante">
              <DataTable
                columns={[
                  { key: 'codigo', header: 'Código' },
                  { key: 'nombre', header: 'Nombre' },
                  { key: 'descripcion', header: 'Descripción', render: (o) => o.descripcion ?? '—' },
                ]}
                rows={data.politicasCampoFaltante}
                getRowKey={(o) => o.codigo}
              />
            </Card>

            <Card title="Reglas de resolución de tipoResultadoWidget">
              <DataTable
                columns={[
                  { key: 'tipoVisualizacion', header: 'Visualización' },
                  { key: 'tipoResultadoSiDistribucion', header: 'Si DISTRIBUCION' },
                  { key: 'tipoResultadoConAgrupacion', header: 'Con agrupación' },
                  { key: 'tipoResultadoSinAgrupacion', header: 'Sin agrupación' },
                ]}
                rows={data.reglasCompatibilidad.resolucionTipoResultadoWidget}
                getRowKey={(r) => r.tipoVisualizacion}
              />
            </Card>
          </>
        )}
      </StateContainer>
    </div>
  )
}
