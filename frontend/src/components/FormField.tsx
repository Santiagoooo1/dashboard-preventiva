import type { ReactNode } from 'react'
import styles from './FormField.module.css'

interface FormFieldProps {
  label: string
  help?: string
  aviso?: string
  error?: string
  children: ReactNode
}

export function FormField({ label, help, aviso, error, children }: FormFieldProps) {
  return (
    <div className={styles.field}>
      <label className={styles.label}>{label}</label>
      {children}
      {help && <p className={styles.help}>{help}</p>}
      {aviso && (
        <p className={styles.aviso} role="alert">
          {aviso}
        </p>
      )}
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
