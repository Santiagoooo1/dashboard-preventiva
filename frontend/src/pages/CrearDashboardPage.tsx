import { useParams } from 'react-router'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { Card } from '../components/Card'
import { GuidedImportWizard } from '../components/importacion-guiada/GuidedImportWizard'
import styles from './CrearDashboardPage.module.css'

const PASOS_FLUJO = [
  'Subir archivo',
  'Revisar columnas',
  'Corregir errores si existen',
  'Importar datos',
  'Crear dashboard inicial',
]

export function CrearDashboardPage() {
  // Presente solo en /crear-dashboard/borrador/:datasetId (reanudar un
  // dataset BORRADOR/VALIDANDO desde "Pruebas y borradores").
  const { datasetId } = useParams<{ datasetId?: string }>()
  const borradorId = datasetId !== undefined ? Number(datasetId) : undefined

  return (
    <div className={styles.page}>
      <Breadcrumbs items={[{ label: 'Inicio', to: '/' }, { label: 'Crear dashboard' }]} />
      <h1>Crear dashboard desde Excel/CSV</h1>
      <Card className={styles.introCard}>
        <p className={styles.intro}>
          Sube un Excel o CSV clínico. La aplicación detectará columnas, validará errores, permitirá corregir datos
          sin modificar el archivo original y generará un dashboard inicial.
        </p>
        <ol className={styles.pasosFlujo}>
          {PASOS_FLUJO.map((paso) => (
            <li key={paso}>{paso}</li>
          ))}
        </ol>
      </Card>
      <GuidedImportWizard borradorId={borradorId} />
    </div>
  )
}
