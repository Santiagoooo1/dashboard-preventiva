import type { ErrorImportacionTrabajoDto, FilaImportacionTrabajoResponseDto } from '../../api/types'
import { identificadoresFila } from '../../utils/importacionGuiada/identificadoresFilaTrabajo'
import { etiquetaBadgeSeveridad, mensajeSeveridad } from '../../utils/importacionGuiada/mensajesSeveridad'
import { EditorCeldaTrabajo } from './EditorCeldaTrabajo'
import styles from './CorreccionFilasTrabajo.module.css'

// El backend no guarda un motivo explícito de exclusión: se infiere a partir
// del error bloqueante que tenía la fila en el momento de excluirla. Si no
// hay ninguno (se excluyó manualmente sin error, o ya no queda ninguno tras
// corregirla), se usa un texto genérico.
function motivoExclusion(errores: ErrorImportacionTrabajoDto[]): string {
  const bloqueante = errores.find((e) => e.severidad === 'ERROR')
  if (bloqueante?.nombreColumna) {
    return `Excluida por ${bloqueante.nombreColumna} vacío o inválido.`
  }
  return 'Excluida de la importación.'
}

interface FilaTrabajoErrorRowProps {
  fila: FilaImportacionTrabajoResponseDto
  tiposPorColumna: Map<string, string>
  disabled: boolean
  onGuardarCelda: (numeroFila: number, columna: string, valor: string) => void
  onDeshacerCelda: (numeroFila: number, columna: string) => void
  onExcluirFila: (numeroFila: number) => void
  onIncluirFila: (numeroFila: number) => void
  onExcluirSimilares: (tipoError: string, nombreColumna: string) => void
  onDeshacerCorreccionesFila: (numeroFila: number) => void
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
  onDeshacerCorreccionesFila,
}: FilaTrabajoErrorRowProps) {
  const identificadores = identificadoresFila(fila.valoresOriginales, fila.valoresCorregidos)
  const tieneCorreccionesEnFila = Object.keys(fila.valoresCorregidos).length > 0

  return (
    <div className={styles.filaBloque}>
      <div className={styles.filaCabecera}>
        <strong>Fila Excel {fila.numeroFilaOriginal}</strong>
        {fila.excluida && <span className={styles.badgeExcluida}>Excluida</span>}
        <div className={styles.filaAcciones}>
          {tieneCorreccionesEnFila && (
            <button
              type="button"
              className="btn btnSecondary"
              disabled={disabled}
              onClick={() => onDeshacerCorreccionesFila(fila.numeroFilaOriginal)}
            >
              Deshacer correcciones de esta fila
            </button>
          )}
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

      {identificadores.length > 0 && (
        <p className={styles.identificadoresFila}>
          {identificadores.map((i) => `${i.etiqueta}: ${i.valor}`).join(' · ')}
        </p>
      )}

      {fila.excluida && (
        <p className={styles.notaExclusion}>
          {motivoExclusion(fila.errores)} Esta fila se omitirá en la importación. El archivo original no se
          modificará.
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
              <th>Severidad</th>
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
              const esBloqueante = error.severidad === 'ERROR'

              return (
                <tr key={columna}>
                  <td>{columna || '—'}</td>
                  <td>{valorOriginal || '—'}</td>
                  <td>{tieneCorreccion ? valorCorregido : '—'}</td>
                  <td>{mensajeSeveridad(error.tipoError, esBloqueante)}</td>
                  <td>
                    <span className={esBloqueante ? styles.badgeErrorBloqueante : styles.badgeAdvertencia}>
                      {etiquetaBadgeSeveridad(esBloqueante)}
                    </span>
                  </td>
                  <td>
                    <div className={styles.accionesCelda}>
                      <EditorCeldaTrabajo
                        tipoDato={tipoDato}
                        valorInicial={tieneCorreccion ? (valorCorregido as string) : valorOriginal}
                        disabled={disabled}
                        esBloqueante={esBloqueante}
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
                        className="btn btnDanger"
                        disabled={disabled}
                        title="Se omitirán las filas que tengan el mismo tipo de error en esta columna."
                        onClick={() => {
                          if (
                            window.confirm(
                              'Vas a excluir todas las filas con este mismo problema. El archivo original no se modificará. Solo debes hacerlo si esas filas no son pacientes válidos o no deben importarse.',
                            )
                          ) {
                            onExcluirSimilares(error.tipoError, columna)
                          }
                        }}
                      >
                        Excluir todas las filas con este mismo problema
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
