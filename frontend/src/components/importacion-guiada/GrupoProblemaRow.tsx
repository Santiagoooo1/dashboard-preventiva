import { useState } from 'react'
import type { GrupoErrorColumna } from '../../utils/importacionGuiada/sugerenciasErrores'
import { esColumnaClinicaCritica } from '../../utils/importacionGuiada/identificadoresFilaTrabajo'
import { etiquetaBadgeSeveridad, mensajeSeveridad } from '../../utils/importacionGuiada/mensajesSeveridad'
import styles from './CorreccionFilasTrabajo.module.css'

const DESCRIPCION_NORMALIZACION: Record<string, string> = {
  FECHA: 'Convierte las fechas reconocibles (dd/mm/aaaa, dd-mm-aaaa...) al formato aaaa-mm-dd.',
  BOOLEANO: 'Convierte valores como sí/verdadero/1 o no/falso/0 a Sí/No.',
  NUMERO: 'Cambia la coma decimal por punto.',
  TEXTO_TRIM: 'Quita espacios sobrantes al principio, al final y entre palabras.',
}

interface GrupoProblemaRowProps {
  grupo: GrupoErrorColumna
  bloqueante: boolean
  expandido: boolean
  disabled: boolean
  onToggleExpandir: () => void
  onExcluirSimilares: (tipoError: string, nombreColumna: string, totalFilas?: number) => void
  /** Solo advertencias: oculta el grupo únicamente en esta pantalla (no llama al backend). */
  onOcultar?: () => void
  /** Solo si el problema predominante es de valores vacíos. */
  onRellenarColumna?: (valor: string) => void
  /** Presente solo si se conoce el tipo de dato de la columna (permite elegir una estrategia segura). */
  onNormalizarColumna?: () => void
  estrategiaNormalizacion?: string | null
  /** true si esta columna es un dato de seguimiento clínico que puede faltar legítimamente. */
  advertenciaClinicaEsperable?: boolean
  /** Necesario para el bloque especial de "columna no reconocida" (volver a revisar mapeos). */
  onVolverAColumnas?: () => void
}

export function GrupoProblemaRow({
  grupo,
  bloqueante,
  expandido,
  disabled,
  onToggleExpandir,
  onExcluirSimilares,
  onOcultar,
  onRellenarColumna,
  onNormalizarColumna,
  estrategiaNormalizacion,
  advertenciaClinicaEsperable,
  onVolverAColumnas,
}: GrupoProblemaRowProps) {
  const [mostrarRellenar, setMostrarRellenar] = useState(false)
  const [valorRellenar, setValorRellenar] = useState('')
  const [avisoVacio, setAvisoVacio] = useState(false)

  // Caso especial: una columna del archivo no está mapeada a ningún campo
  // clínico de la plantilla. No es un dato de paciente incorrecto, es un
  // problema de configuración de columnas/cabecera, así que no tiene sentido
  // ofrecer "ver filas afectadas" ni "excluir filas": no hay filas que corregir.
  if (grupo.tipoErrorPredominante === 'COLUMNA_NO_RECONOCIDA') {
    return (
      <div className={styles.grupoProblema}>
        <div className={styles.grupoProblemaCabecera}>
          <span className={styles.badgeAdvertencia}>Columna no reconocida</span>
          <strong>{grupo.nombreColumna}</strong>
        </div>
        <p className={styles.notaAdvertencia}>
          La aplicación no sabe a qué campo clínico corresponde esta columna. Debes ignorarla, mapearla o cambiar la
          fila de cabecera antes de importar.
        </p>
        {grupo.valoresEjemplo.length > 0 && (
          <p className={styles.notaAdvertencia}>Valor detectado: {grupo.valoresEjemplo[0]}</p>
        )}
        <p className={styles.notaAdvertencia}>
          Para ignorarla: vuelve al paso Columnas y desmárcala. Para cambiar la fila de cabecera: vuelve a Subir
          archivo y ajústala allí.
        </p>
        {onVolverAColumnas && (
          <div className={styles.grupoProblemaAcciones}>
            <button type="button" className="btn btnSecondary" disabled={disabled} onClick={onVolverAColumnas}>
              Volver a columnas
            </button>
          </div>
        )}
      </div>
    )
  }

  const esCritica = esColumnaClinicaCritica(grupo.nombreColumna)

  const aplicarRellenar = () => {
    if (valorRellenar.trim() === '') {
      setAvisoVacio(true)
      return
    }
    if (
      esCritica &&
      !window.confirm(
        `Vas a rellenar "${grupo.nombreColumna}" en varias filas: es un campo clínico crítico. Asegúrate de que el valor "${valorRellenar.trim()}" es correcto. ¿Continuar?`,
      )
    ) {
      return
    }
    onRellenarColumna?.(valorRellenar.trim())
    setValorRellenar('')
    setMostrarRellenar(false)
    setAvisoVacio(false)
  }

  const excluirSimilares = () => {
    if (
      !window.confirm(
        `Vas a excluir ${grupo.totalErrores} fila(s) de la importación. El archivo original no se modificará. Solo debes hacerlo si esas filas no son pacientes válidos o no deben importarse.`,
      )
    ) {
      return
    }
    onExcluirSimilares(grupo.tipoErrorPredominante, grupo.nombreColumna, grupo.totalErrores)
  }

  return (
    <div className={styles.grupoProblema}>
      <div className={styles.grupoProblemaCabecera}>
        <span className={bloqueante ? styles.badgeErrorBloqueante : styles.badgeAdvertencia}>
          {etiquetaBadgeSeveridad(bloqueante)}
        </span>
        <strong>{grupo.nombreColumna}</strong>
        <span>{mensajeSeveridad(grupo.tipoErrorPredominante, bloqueante)}</span>
        <span className={styles.grupoProblemaContador}>
          {grupo.totalErrores} fila{grupo.totalErrores === 1 ? '' : 's'} afectada{grupo.totalErrores === 1 ? '' : 's'}
        </span>
      </div>
      {advertenciaClinicaEsperable && (
        <p className={styles.notaAdvertencia}>Este campo puede estar vacío si no aplica al paciente.</p>
      )}
      <div className={styles.grupoProblemaAcciones}>
        <button type="button" className="btn btnSecondary" disabled={disabled} onClick={onToggleExpandir}>
          {expandido ? 'Ocultar filas afectadas' : 'Ver filas afectadas'}
        </button>
        <button
          type="button"
          className="btn btnDanger"
          disabled={disabled}
          title="Se omitirán las filas que tengan el mismo tipo de error en esta columna."
          onClick={excluirSimilares}
        >
          Excluir todas las filas con este mismo problema
        </button>
        {onOcultar && (
          <button type="button" className="btn btnSecondary" disabled={disabled} onClick={onOcultar}>
            Ocultar este tipo de advertencia
          </button>
        )}
      </div>

      {(onRellenarColumna || onNormalizarColumna) && (
        <div className={styles.correccionesSugeridas}>
          <p className={styles.correccionesSugeridasTitulo}>Correcciones sugeridas</p>
          <p className={styles.notaAdvertencia}>
            {advertenciaClinicaEsperable
              ? 'No rellenes este campo si no aplica clínicamente.'
              : 'Puedes corregir este dato manualmente fila a fila, o aplicar una corrección en bloque a las filas con este problema.'}
          </p>
          <div className={styles.grupoProblemaAcciones}>
            {onRellenarColumna && !mostrarRellenar && (
              <button
                type="button"
                className="btn btnSecondary"
                disabled={disabled}
                onClick={() => setMostrarRellenar(true)}
              >
                Rellenar todas las celdas vacías de esta columna
              </button>
            )}
            {onNormalizarColumna && (
              <button
                type="button"
                className="btn btnSecondary"
                disabled={disabled}
                title={estrategiaNormalizacion ? DESCRIPCION_NORMALIZACION[estrategiaNormalizacion] : undefined}
                onClick={onNormalizarColumna}
              >
                Normalizar valores de esta columna
              </button>
            )}
          </div>
          {onRellenarColumna && mostrarRellenar && (
            <div className={styles.editorCelda} style={{ marginTop: 'var(--spacing-xs)' }}>
              <input
                type="text"
                value={valorRellenar}
                disabled={disabled}
                placeholder="Valor a aplicar"
                onChange={(e) => {
                  setValorRellenar(e.target.value)
                  setAvisoVacio(false)
                }}
              />
              <button type="button" className="btn btnPrimary" disabled={disabled} onClick={aplicarRellenar}>
                Aplicar a {grupo.totalErrores} fila{grupo.totalErrores === 1 ? '' : 's'}
              </button>
              <button
                type="button"
                className="btn btnSecondary"
                disabled={disabled}
                onClick={() => {
                  setMostrarRellenar(false)
                  setValorRellenar('')
                  setAvisoVacio(false)
                }}
              >
                Cancelar
              </button>
            </div>
          )}
          {avisoVacio && <p className={styles.avisoVacio}>Introduce un valor o excluye la fila.</p>}
        </div>
      )}
    </div>
  )
}
