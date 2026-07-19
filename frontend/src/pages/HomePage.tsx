import { Link } from 'react-router'
import { Card } from '../components/Card'
import styles from './HomePage.module.css'

export function HomePage() {
  return (
    <div className={styles.page}>
      <h1>Dashboard Preventiva</h1>
      <p>Sistema de monitorización y prevención de infección de sitio quirúrgico (ILQ).</p>
      <div className={styles.grid}>
        <Card title="Datasets clínicos">
          <p>Consulta los datasets clínicos configurados, sus campos, métricas y paneles.</p>
          <Link to="/datasets">Ver datasets →</Link>
        </Card>
        <Card title="Catálogo técnico">
          <p>Consulta los tipos, operadores y reglas de compatibilidad que expone el backend.</p>
          <Link to="/catalogo">Ver catálogo →</Link>
        </Card>
      </div>
    </div>
  )
}
