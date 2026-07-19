import { Outlet } from 'react-router'
import { Navbar } from './Navbar'
import styles from './MainLayout.module.css'

export function MainLayout() {
  return (
    <div className={styles.wrapper}>
      <Navbar />
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  )
}
