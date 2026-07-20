import { Fragment } from 'react'
import { Link } from 'react-router'
import styles from './Breadcrumbs.module.css'

export interface Crumb {
  label: string
  to?: string
}

interface BreadcrumbsProps {
  items: Crumb[]
}

export function Breadcrumbs({ items }: BreadcrumbsProps) {
  return (
    <nav className={styles.breadcrumbs} aria-label="Ruta de navegación">
      {items.map((item, index) => (
        <Fragment key={`${item.label}-${index}`}>
          {item.to ? <Link to={item.to}>{item.label}</Link> : <span className={styles.current}>{item.label}</span>}
          {index < items.length - 1 && <span className={styles.sep}>/</span>}
        </Fragment>
      ))}
    </nav>
  )
}
