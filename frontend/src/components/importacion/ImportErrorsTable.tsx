import type { ErrorFilaImportacionGenericaDto, ErrorImportacionGenericaResponseDto } from '../../api/types'
import { DataTable } from '../DataTable'
import styles from './Importacion.module.css'

const MAXIMO_VISIBLE = 100

// Sirve para los errores de validación y para los persistidos de una importación:
// ambos DTOs comparten estos seis campos.
type ErrorImportacion = ErrorFilaImportacionGenericaDto | ErrorImportacionGenericaResponseDto

interface ImportErrorsTableProps {
  errores: ErrorImportacion[]
  mensajeVacio?: string
}

export function ImportErrorsTable({ errores, mensajeVacio }: ImportErrorsTableProps) {
  if (errores.length === 0) {
    return <p className="stateEmpty">{mensajeVacio ?? 'Sin errores.'}</p>
  }

  const visibles = errores.slice(0, MAXIMO_VISIBLE)

  return (
    <div>
      <div className={styles.scroll}>
        <DataTable
          columns={[
            { key: 'numeroFila', header: 'Fila', render: (e) => e.numeroFila ?? '—' },
            { key: 'nombreColumna', header: 'Columna', render: (e) => e.nombreColumna ?? '—' },
            { key: 'valorOriginal', header: 'Valor', render: (e) => e.valorOriginal ?? '—' },
            { key: 'tipoError', header: 'Tipo' },
            {
              key: 'severidad',
              header: 'Severidad',
              render: (e) => (
                <span className={e.severidad === 'ERROR' ? styles.badgeError : styles.badgeAviso}>
                  {e.severidad}
                </span>
              ),
            },
            { key: 'mensaje', header: 'Mensaje' },
          ]}
          rows={visibles}
          getRowKey={(e, i) => `${e.numeroFila ?? 'x'}-${e.nombreColumna ?? 'x'}-${i}`}
        />
      </div>
      {errores.length > MAXIMO_VISIBLE && (
        <p className={styles.aviso100}>
          Mostrando {MAXIMO_VISIBLE} de {errores.length} errores.
        </p>
      )}
    </div>
  )
}
