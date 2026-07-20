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
        <NavLink to="/datasets" className={({ isActive }) => (isActive ? styles.active : undefined)}>
          Datasets
        </NavLink>
        <NavLink to="/catalogo" className={({ isActive }) => (isActive ? styles.active : undefined)}>
          Catálogo
        </NavLink>
      </nav>
      <BackendStatusBadge />
    </header>
  )
}
