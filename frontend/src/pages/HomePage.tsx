import { Link } from 'react-router'
import { useApiResource } from '../hooks/useApiResource'
import { listarDatasets } from '../api/datasetApi'
import { Card } from '../components/Card'
import styles from './HomePage.module.css'

interface Paso {
  titulo: string
  descripcion: string
  enlace?: { to: string; texto: string }
}

const PASOS: Paso[] = [
  {
    titulo: 'Sube tu archivo Excel/CSV',
    descripcion: 'La aplicación detecta las columnas, crea el dataset e importa los registros por ti.',
    enlace: { to: '/crear-dashboard', texto: 'Crear dashboard' },
  },
  {
    titulo: 'Revisa las columnas',
    descripcion: 'Ajusta nombres visibles, tipos de dato y rol clínico de cada columna detectada.',
  },
  {
    titulo: 'Importa los registros',
    descripcion: 'La aplicación valida columnas y filas e incorpora los datos al dataset.',
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

      <Card title="Empieza aquí">
        <p>
          Sube un archivo Excel o CSV con tus datos clínicos y la aplicación creará el dashboard por ti: detecta las
          columnas, importa los registros y te lleva a los indicadores.
        </p>
        <div className={styles.cta}>
          <Link className="btn btnPrimary" to="/crear-dashboard">
            Crear dashboard desde Excel/CSV
          </Link>
        </div>
      </Card>

      <Card title="Configuración avanzada">
        {loading ? (
          <p className="stateLoading" role="status">
            Cargando…
          </p>
        ) : error ? (
          <p className="stateError" role="alert">
            Error: {error}
          </p>
        ) : (
          <>
            <p>
              Gestiona manualmente datasets, campos, indicadores y paneles.{' '}
              {hayDatasets
                ? `Actualmente hay ${totalDatasets} ${totalDatasets === 1 ? 'dataset activo' : 'datasets activos'}.`
                : 'Todavía no hay datasets.'}
            </p>
            <div className={styles.cta}>
              <Link className="btn btnSecondary" to="/datasets">
                Ver datasets
              </Link>
              <Link className="btn btnSecondary" to="/datasets/nuevo">
                Crear dataset manualmente
              </Link>
            </div>
          </>
        )}
      </Card>

      <Card title="Flujo recomendado">
        <ol className={styles.pasos}>
          {PASOS.map((paso) => (
            <li key={paso.titulo}>
              <p className={styles.pasoTitulo}>{paso.titulo}</p>
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
