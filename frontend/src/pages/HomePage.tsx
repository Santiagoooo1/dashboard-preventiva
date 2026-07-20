import { Link } from 'react-router'
import { useApiResource } from '../hooks/useApiResource'
import { listarDatasets } from '../api/datasetApi'
import { Card } from '../components/Card'
import styles from './HomePage.module.css'

interface Paso {
  titulo: string
  descripcion: string
  enlace?: { to: string; texto: string }
  proximamente?: boolean
}

const PASOS: Paso[] = [
  {
    titulo: 'Crear dataset clínico',
    descripcion: 'Define la fuente de datos de tu especialidad: caídas, infecciones, cirugías, urgencias…',
    enlace: { to: '/datasets/nuevo', texto: 'Crear dataset' },
  },
  {
    titulo: 'Definir campos',
    descripcion: 'Estructura los datos del dataset: fechas, servicios, valores numéricos, categorías.',
  },
  {
    titulo: 'Importar datos',
    descripcion: 'Importa datos desde Excel/CSV cuando la funcionalidad esté disponible.',
    proximamente: true,
  },
  {
    titulo: 'Crear métricas',
    descripcion: 'Define indicadores clínicos: conteos, tasas, promedios, sumas y distribuciones.',
  },
  {
    titulo: 'Crear panel',
    descripcion: 'Agrupa las métricas relevantes en un panel clínico.',
  },
  {
    titulo: 'Configurar widgets y ver dashboard',
    descripcion: 'Elige la visualización y el tipo de resultado de cada widget, y consulta el dashboard.',
  },
]

export function HomePage() {
  const { data, loading, error } = useApiResource((signal) => listarDatasets(signal), [])
  const totalDatasets = data?.length ?? 0
  const hayDatasets = totalDatasets > 0

  return (
    <div className={styles.page}>
      <h1>Dashboard Clínico</h1>
      <p className={styles.descripcion}>
        Plataforma para crear dashboards clínicos configurables por especialidad médica. Define datasets, campos,
        métricas, paneles y widgets para analizar datos clínicos sin programar.
      </p>
      <p className={styles.especialidades}>
        Pensada para cualquier especialidad: Cardiología, Traumatología, Neurología, UCI, Urgencias, Medicina
        Preventiva, Cirugía, Oncología, Digestivo, Ginecología o Pediatría.
      </p>

      <Card title="Datasets clínicos">
        {loading ? (
          <p className="stateLoading" role="status">
            Cargando…
          </p>
        ) : error ? (
          <p className="stateError" role="alert">
            Error: {error}
          </p>
        ) : hayDatasets ? (
          <>
            <p className={styles.resumenValor}>
              {totalDatasets} {totalDatasets === 1 ? 'dataset activo' : 'datasets activos'}
            </p>
            <div className={styles.cta}>
              <Link className="btn btnPrimary" to="/datasets">
                Ver datasets
              </Link>
              <Link className="btn btnSecondary" to="/datasets/nuevo">
                Crear nuevo dataset
              </Link>
            </div>
          </>
        ) : (
          <>
            <p>Aún no hay datasets. Empieza creando uno.</p>
            <div className={styles.cta}>
              <Link className="btn btnPrimary" to="/datasets/nuevo">
                Crear primer dataset
              </Link>
            </div>
          </>
        )}
      </Card>

      <Card title="Flujo recomendado">
        <ol className={styles.pasos}>
          {PASOS.map((paso) => (
            <li key={paso.titulo}>
              <p className={styles.pasoTitulo}>
                {paso.titulo} {paso.proximamente && <span className={styles.proximamente}>Próximamente</span>}
              </p>
              <p className={styles.pasoDescripcion}>{paso.descripcion}</p>
              {paso.enlace && <Link to={paso.enlace.to}>{paso.enlace.texto} →</Link>}
            </li>
          ))}
        </ol>
      </Card>

      <Card title="Catálogo técnico">
        <p>Consulta los tipos de métrica, operadores y reglas de compatibilidad que expone el backend.</p>
        <Link to="/catalogo">Ver catálogo →</Link>
      </Card>
    </div>
  )
}
