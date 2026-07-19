import type { ReactNode } from 'react'
import styles from './Card.module.css'

interface CardProps {
  title?: string
  subtitle?: string
  className?: string
  children: ReactNode
}

export function Card({ title, subtitle, className, children }: CardProps) {
  return (
    <div className={`${styles.card} ${className ?? ''}`}>
      {title && <h3 className={styles.title}>{title}</h3>}
      {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
      <div>{children}</div>
    </div>
  )
}
