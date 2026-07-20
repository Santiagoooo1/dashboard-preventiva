import { Breadcrumbs } from '../components/Breadcrumbs'
import { GuidedImportWizard } from '../components/importacion-guiada/GuidedImportWizard'
import styles from './CrearDashboardPage.module.css'

export function CrearDashboardPage() {
  return (
    <div className={styles.page}>
      <Breadcrumbs items={[{ label: 'Inicio', to: '/' }, { label: 'Crear dashboard' }]} />
      <h1>Crear dashboard desde Excel/CSV</h1>
      <GuidedImportWizard />
    </div>
  )
}
