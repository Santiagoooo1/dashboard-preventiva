import { NavLink } from 'react-router'
import { BackendStatusBadge } from '../components/BackendStatusBadge'
import styles from './Navbar.module.css'

export function Navbar() {
  return (
    <header className={styles.navbar}>
      <span className={styles.brand}>Dashboard Clínico</span>
      <nav className={styles.links}>
        <NavLink to="/" end className={({ isActive }) => (isActive ? styles.active : undefined)}>
          Inicio
        </NavLink>
        <NavLink to="/crear-dashboard" className={({ isActive }) => (isActive ? styles.active : undefined)}>
          Crear dashboard
        </NavLink>
        <NavLink to="/comparar-anios" className={({ isActive }) => (isActive ? styles.active : undefined)}>
          Comparar años
        </NavLink>
        <span className={styles.grupoAvanzado}>
          <span className={styles.grupoAvanzadoEtiqueta}>Avanzado:</span>
          <NavLink to="/datasets" className={({ isActive }) => (isActive ? styles.active : undefined)}>
            Datasets
          </NavLink>
          <NavLink to="/catalogo" className={({ isActive }) => (isActive ? styles.active : undefined)}>
            Catálogo
          </NavLink>
        </span>
      </nav>
      <span className={styles.estado}>
        <BackendStatusBadge />
      </span>
    </header>
  )
}
