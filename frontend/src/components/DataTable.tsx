import type { ReactNode } from 'react'
import styles from './DataTable.module.css'

export interface DataTableColumn<T> {
  key: string
  header: string
  render?: (row: T) => ReactNode
}

/**
 * Interacción opcional por fila (cross-filtering, 6.9H.1). Solo debe pasarse en
 * tablas que representen resultados AGREGADOS por una categoría; una tabla de
 * registros crudos no tiene un valor de filtro inequívoco por fila.
 */
export interface DataTableFilaInteractiva<T> {
  /** Null en filas no seleccionables: esa fila queda inerte. */
  getValorSeleccion: (row: T) => { valorOriginal: string; etiquetaVisible: string } | null
  valorSeleccionado: string | null
  etiquetaCampo: string
  onSeleccionar: (valorOriginal: string, etiquetaVisible: string) => void
  claseFila: string
  claseFilaActiva: string
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[]
  rows: T[]
  getRowKey: (row: T, index: number) => string | number
  filaInteractiva?: DataTableFilaInteractiva<T>
}

function valorPorDefecto<T>(row: T, key: string): ReactNode {
  const valor = (row as Record<string, unknown>)[key]
  return valor === null || valor === undefined ? '—' : (valor as ReactNode)
}

export function DataTable<T>({ columns, rows, getRowKey, filaInteractiva }: DataTableProps<T>) {
  const propsFila = (row: T) => {
    if (!filaInteractiva) return {}
    const seleccion = filaInteractiva.getValorSeleccion(row)
    if (!seleccion) return {}

    const activa = filaInteractiva.valorSeleccionado === seleccion.valorOriginal
    const seleccionar = () => filaInteractiva.onSeleccionar(seleccion.valorOriginal, seleccion.etiquetaVisible)

    return {
      className: `${filaInteractiva.claseFila} ${activa ? filaInteractiva.claseFilaActiva : ''}`,
      role: 'button',
      tabIndex: 0,
      'aria-selected': activa,
      'aria-label': activa
        ? `${filaInteractiva.etiquetaCampo}: ${seleccion.etiquetaVisible} seleccionado. Pulsar para quitar.`
        : `Seleccionar ${filaInteractiva.etiquetaCampo}: ${seleccion.etiquetaVisible}.`,
      onClick: seleccionar,
      onKeyDown: (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          seleccionar()
        }
      },
    }
  }

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
            <tr key={getRowKey(row, index)} {...propsFila(row)}>
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
