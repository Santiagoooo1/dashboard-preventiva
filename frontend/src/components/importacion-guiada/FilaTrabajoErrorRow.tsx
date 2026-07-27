import type { FilaImportacionTrabajoResponseDto } from '../../api/types'
import { EditorCeldaTrabajo } from './EditorCeldaTrabajo'
import styles from './CorreccionFilasTrabajo.module.css'

interface FilaTrabajoErrorRowProps {
  fila: FilaImportacionTrabajoResponseDto
  tiposPorColumna: Map<string, string>
  disabled: boolean
  onGuardarCelda: (numeroFila: number, columna: string, valor: string) => void
  onDeshacerCelda: (numeroFila: number, columna: string) => void
  onExcluirFila: (numeroFila: number) => void
  onIncluirFila: (numeroFila: number) => void
  onExcluirSimilares: (tipoError: string, nombreColumna: string) => void
}

export function FilaTrabajoErrorRow({
  fila,
  tiposPorColumna,
  disabled,
  onGuardarCelda,
  onDeshacerCelda,
  onExcluirFila,
  onIncluirFila,
  onExcluirSimilares,
}: FilaTrabajoErrorRowProps) {
  return (
    <div className={styles.filaBloque}>
      <div className={styles.filaCabecera}>
        <strong>Fila {fila.numeroFilaOriginal}</strong>
        {fila.excluida && <span className={styles.badgeExcluida}>Excluida</span>}
        <div className={styles.filaAcciones}>
          {fila.excluida ? (
            <button
              type="button"
              className="btn btnSecondary"
              disabled={disabled}
              onClick={() => onIncluirFila(fila.numeroFilaOriginal)}
            >
              Deshacer exclusión
            </button>
          ) : (
            <button
              type="button"
              className="btn btnSecondary"
              disabled={disabled}
              onClick={() => onExcluirFila(fila.numeroFilaOriginal)}
            >
              Excluir fila
            </button>
          )}
        </div>
      </div>

      {fila.excluida && (
        <p className={styles.notaExclusion}>
          Esta fila se omitirá en la importación. El archivo original no se modificará.
        </p>
      )}

      <div className={styles.tablaErroresScroll}>
        <table className={styles.tablaErrores}>
          <thead>
            <tr>
              <th>Columna</th>
              <th>Valor original</th>
              <th>Valor corregido</th>
              <th>Problema</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {fila.errores.map((error) => {
              const columna = error.nombreColumna ?? ''
              const valorOriginal = fila.valoresOriginales[columna] ?? ''
              const valorCorregido = fila.valoresCorregidos[columna]
              const tieneCorreccion = valorCorregido !== undefined && valorCorregido !== null
              const tipoDato = tiposPorColumna.get(columna) ?? null

              return (
                <tr key={columna}>
                  <td>{columna || '—'}</td>
                  <td>{valorOriginal || '—'}</td>
                  <td>{tieneCorreccion ? valorCorregido : '—'}</td>
                  <td>{error.mensaje}</td>
                  <td>
                    <div className={styles.accionesCelda}>
                      <EditorCeldaTrabajo
                        tipoDato={tipoDato}
                        valorInicial={tieneCorreccion ? (valorCorregido as string) : valorOriginal}
                        disabled={disabled}
                        onGuardar={(valor) => onGuardarCelda(fila.numeroFilaOriginal, columna, valor)}
                      />
                      {tieneCorreccion && (
                        <button
                          type="button"
                          className="btn btnSecondary"
                          disabled={disabled}
                          onClick={() => onDeshacerCelda(fila.numeroFilaOriginal, columna)}
                        >
                          Deshacer corrección
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn btnSecondary"
                        disabled={disabled}
                        onClick={() => onExcluirSimilares(error.tipoError, columna)}
                      >
                        Excluir todas las filas con este problema
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
