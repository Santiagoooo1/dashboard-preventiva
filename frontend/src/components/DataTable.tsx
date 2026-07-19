import type { ReactNode } from 'react'
import styles from './DataTable.module.css'

export interface DataTableColumn<T> {
  key: string
  header: string
  render?: (row: T) => ReactNode
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[]
  rows: T[]
  getRowKey: (row: T, index: number) => string | number
}

function valorPorDefecto<T>(row: T, key: string): ReactNode {
  const valor = (row as Record<string, unknown>)[key]
  return valor === null || valor === undefined ? '—' : (valor as ReactNode)
}

export function DataTable<T>({ columns, rows, getRowKey }: DataTableProps<T>) {
  return (
    <div className={styles.scroll}>
      <table className={styles.table}>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={getRowKey(row, index)}>
              {columns.map((column) => (
                <td key={column.key}>{column.render ? column.render(row) : valorPorDefecto(row, column.key)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
