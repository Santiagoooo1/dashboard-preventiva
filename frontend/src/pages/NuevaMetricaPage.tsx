import { Link, useParams } from 'react-router'
import { obtenerFrontendMetadata } from '../api/datasetApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Breadcrumbs } from '../components/Breadcrumbs'
import styles from './NuevaMetricaPage.module.css'

interface ModoCreacion {
  ruta: string
  titulo: string
  resumen: string
  ejemplos: string[]
  recomendado?: boolean
}

const MODOS: ModoCreacion[] = [
  {
    ruta: 'desde-columna',
    titulo: 'Desde una columna',
    resumen:
      'Eliges una columna de tu Excel y el sistema te propone qué se puede medir con ella. Es la vía más rápida y la que evita configuraciones imposibles.',
    ejemplos: ['Edad media', 'Pacientes distintos', 'Reparto de ASA', 'Completitud de un campo'],
    recomendado: true,
  },
  {
    ruta: 'porcentaje',
    titulo: 'Porcentaje condicional',
    resumen:
      'Defines por separado la población de partida, qué cuenta arriba y qué cuenta abajo. Pensado para indicadores donde el denominador no es «todos los registros».',
    ejemplos: ['% de profilaxis adecuada entre los casos en que estaba indicada', 'Tasa de ILQ sobre casos con seguimiento'],
  },
  {
    ruta: 'avanzada',
    titulo: 'Métrica avanzada',
    resumen:
      'El formulario completo: eliges el tipo de cálculo y montas los filtros a mano. Útil cuando ya sabes exactamente qué configuración quieres.',
    ejemplos: ['Cualquier combinación de las anteriores'],
  },
]

/**
 * Punto de entrada a la creación de métricas (Fase 6.9I.2).
 *
 * <p>Los tres modos no son tres motores: los tres producen una `MetricaClinica`
 * con la misma estructura de configuración y los ejecuta el mismo servicio.
 * Solo cambia cuánto tiene que saber el usuario para llegar hasta ella.
 */
export function NuevaMetricaPage() {
  const { datasetId } = useParams<{ datasetId: string }>()

  const { data, loading, error } = useApiResource(
    async (signal) => obtenerFrontendMetadata(datasetId ?? '', signal),
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
                { label: 'Métricas', to: `/datasets/${datasetId}/metricas` },
                { label: 'Nueva métrica' },
              ]}
            />
            <h1>Nueva métrica</h1>
            <p className={styles.intro}>
              Una métrica es un cálculo guardado sobre <strong>{data.dataset.nombre}</strong>. Se crea una vez y
              después puedes mostrarla en uno o varios dashboards, con la visualización que prefieras en cada uno.
            </p>

            <ul className={styles.modos}>
              {MODOS.map((modo) => (
                <li key={modo.ruta}>
                  <Link className={styles.modo} to={`/datasets/${datasetId}/metricas/nueva/${modo.ruta}`}>
                    <span className={styles.modoCabecera}>
                      <span className={styles.modoTitulo}>{modo.titulo}</span>
                      {modo.recomendado && <span className={styles.etiquetaRecomendado}>Recomendado</span>}
                    </span>
                    <span className={styles.modoResumen}>{modo.resumen}</span>
                    <span className={styles.modoEjemplos}>
                      {modo.ejemplos.map((ejemplo) => (
                        <span key={ejemplo} className={styles.ejemplo}>
                          {ejemplo}
                        </span>
                      ))}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>

            <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas`}>
              Volver a métricas
            </Link>
          </>
        )}
      </StateContainer>
    </div>
  )
}
