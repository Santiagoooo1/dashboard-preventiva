import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type {
  MetricaClinicaResponseDto,
  ResultadoMetricaResponseDto,
  ResumenConfiguracionDatasetDto,
} from '../api/types'
import { useApiResource } from '../hooks/useApiResource'
import { obtenerFrontendMetadata } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { DataTable } from '../components/DataTable'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { MetricaRowActions } from '../components/metrics/MetricaRowActions'
import { PanelRowActions } from '../components/paneles/PanelRowActions'
import { WidgetActual } from '../components/widgets/WidgetActual'
import { DashboardInicialCard } from '../components/dashboard/DashboardInicialCard'
import styles from './DatasetDetailPage.module.css'

interface Recomendacion {
  titulo: string
  descripcion: string
  to: string
  textoBoton: string
}

// `avisoDashboardInicial` solo debe usarse cuando la card DashboardInicialCard
// se muestra de verdad encima (dataset ACTIVO): si no, "arriba" no referiría a nada.
function siguientePaso(datasetId: string, resumen: ResumenConfiguracionDatasetDto, estaActivo: boolean): Recomendacion {
  if (resumen.totalCampos === 0) {
    return {
      titulo: 'Define los campos del dataset',
      descripcion:
        'Este dataset aún no tiene campos. Créalos manualmente o usa los campos básicos recomendados (paciente, fecha y servicio).',
      to: `/datasets/${datasetId}/campos`,
      textoBoton: 'Crear campos',
    }
  }
  if (resumen.totalPlantillasImportacion === 0) {
    return {
      titulo: 'Importa datos al dataset',
      descripcion:
        'Ya hay campos definidos. Crea una plantilla de importación para cargar registros desde Excel o CSV.',
      to: `/datasets/${datasetId}/importar`,
      textoBoton: 'Importar datos',
    }
  }
  if (resumen.totalMetricas === 0) {
    return {
      titulo: 'Crea la primera métrica',
      descripcion: estaActivo
        ? 'Ya hay campos definidos. Puedes crear un indicador clínico a mano (conteo, tasa, promedio…), o usar ' +
          '"Crear dashboard inicial" arriba para generar varios de golpe a partir de los campos importados.'
        : 'Ya hay campos definidos. El siguiente paso es crear un indicador clínico: conteo, tasa, promedio…',
      to: `/datasets/${datasetId}/metricas/nueva`,
      textoBoton: 'Nueva métrica',
    }
  }
  if (resumen.totalPaneles === 0) {
    return {
      titulo: 'Crea el primer panel',
      descripcion: estaActivo
        ? 'Ya hay métricas. Agrúpalas en un panel clínico para construir el dashboard, o usa "Crear dashboard inicial" arriba si todavía no lo has hecho.'
        : 'Ya hay métricas. Agrúpalas en un panel clínico para construir el dashboard.',
      to: `/datasets/${datasetId}/paneles/nuevo`,
      textoBoton: 'Nuevo panel',
    }
  }
  return {
    titulo: 'Configura widgets y consulta el dashboard',
    descripcion: 'El dataset ya tiene campos, métricas y paneles. Añade o ajusta widgets y revisa el dashboard.',
    to: `/datasets/${datasetId}/paneles`,
    textoBoton: 'Gestionar paneles',
  }
}

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

  const recomendacion = data
    ? siguientePaso(datasetId ?? '', data.resumenConfiguracion, data.dataset.estadoDataset === 'ACTIVO')
    : null

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && recomendacion && (
          <>
            <Breadcrumbs
              items={[{ label: 'Datasets', to: '/datasets' }, { label: data.dataset.codigo }]}
            />
            <h1>{data.dataset.nombre}</h1>
            <p className={styles.codigo}>{data.dataset.codigo}</p>
            {data.dataset.descripcion && <p>{data.dataset.descripcion}</p>}
            <p className="stateEmpty">Esta es una vista avanzada de configuración.</p>

            {data.dataset.estadoDataset === 'ACTIVO' && <DashboardInicialCard datasetId={Number(datasetId)} />}

            <Card title="Siguiente paso recomendado" className={styles.recomendacion}>
              <p className={styles.recomendacionTitulo}>{recomendacion.titulo}</p>
              <p>{recomendacion.descripcion}</p>
              <Link className="btn btnPrimary" to={recomendacion.to}>
                {recomendacion.textoBoton}
              </Link>
            </Card>

            <Card title="Accesos rápidos">
              <div className={styles.accionesDataset}>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/campos`}>
                  Gestionar campos
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/importar`}>
                  Importar datos
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/plantillas`}>
                  Plantillas de importación
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/metricas`}>
                  Gestionar métricas
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/paneles`}>
                  Gestionar paneles
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/campos/nuevo`}>
                  Nuevo campo
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/metricas/nueva`}>
                  Nueva métrica
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/paneles/nuevo`}>
                  Nuevo panel
                </Link>
                <Link className="btn btnAction" to={`/datasets/${datasetId}/editar`}>
                  Editar datos básicos
                </Link>
              </div>
            </Card>

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
                <p>
                  No hay campos clínicos configurados.{' '}
                  <Link to={`/datasets/${datasetId}/campos`}>Crear campos →</Link>
                </p>
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
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/metricas/nueva`}>
                  + Nueva métrica
                </Link>
                <Link to={`/datasets/${datasetId}/metricas`}>Ver todas las métricas →</Link>
              </div>
              <ErrorBanner mensaje={errorAccion} />
              {data.metricas.length === 0 ? (
                <p>
                  No hay métricas configuradas.{' '}
                  <Link to={`/datasets/${datasetId}/metricas/nueva`}>Crear la primera métrica →</Link>
                </p>
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
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/paneles/nuevo`}>
                  + Nuevo panel
                </Link>
                <Link to={`/datasets/${datasetId}/paneles`}>Ver todos los paneles →</Link>
              </div>
              {data.paneles.length === 0 ? (
                <p>
                  No hay paneles configurados.{' '}
                  <Link to={`/datasets/${datasetId}/paneles/nuevo`}>Crear el primer panel →</Link>
                </p>
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

            <Card title="Trazabilidad reciente">
              <p className="stateEmpty">
                La trazabilidad se muestra desde el resultado de importación o desde la copia interna.
              </p>
            </Card>

            <Card title="Plantillas de importación">
              <div className={styles.metricasAcciones}>
                <Link className="btn btnPrimary" to={`/datasets/${datasetId}/plantillas/nueva`}>
                  + Nueva plantilla
                </Link>
                <Link to={`/datasets/${datasetId}/plantillas`}>Ver todas las plantillas →</Link>
                <Link to={`/datasets/${datasetId}/importar`}>Importar datos →</Link>
              </div>
              {data.campos.length === 0 && (
                <p className="stateEmpty">
                  Define primero los campos clínicos del dataset: los mapeos de una plantilla apuntan a esos campos.
                </p>
              )}
              {data.plantillasImportacion.length === 0 ? (
                <p>
                  No hay plantillas de importación configuradas.{' '}
                  <Link to={`/datasets/${datasetId}/plantillas/nueva`}>Crear plantilla de importación →</Link>
                </p>
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
