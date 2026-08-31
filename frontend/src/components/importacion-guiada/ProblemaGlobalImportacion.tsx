import type { ErrorImportacionTrabajoDto } from '../../api/types'
import { etiquetaBadgeSeveridad } from '../../utils/importacionGuiada/mensajesSeveridad'
import styles from './CorreccionFilasTrabajo.module.css'

interface ProblemaGlobalImportacionProps {
  error: ErrorImportacionTrabajoDto
  bloqueante: boolean
  disabled: boolean
  /** Ausente cuando no hay columnas reconstruidas que revisar (ver Fase 6.8E.2.1). */
  onVolverAColumnas?: () => void
  /** "Sustituir archivo": descarta esta copia a petición del usuario y vuelve a pedir un Excel. */
  onSustituirArchivo: () => void
}

// Errores sin columna asociada (p. ej. el archivo no tiene ninguna fila
// clínica reconocible): no describen una celda ni una columna concretas, así
// que no tiene sentido agruparlos como si fueran una columna llamada
// "Columna desconocida" con filas afectadas que en realidad no existen.
export function ProblemaGlobalImportacion({
  error,
  bloqueante,
  disabled,
  onVolverAColumnas,
  onSustituirArchivo,
}: ProblemaGlobalImportacionProps) {
  return (
    <div className={styles.grupoProblema}>
      <div className={styles.grupoProblemaCabecera}>
        <span className={bloqueante ? styles.badgeErrorBloqueante : styles.badgeAdvertencia}>
          {etiquetaBadgeSeveridad(bloqueante)}
        </span>
        <strong>Problema con el archivo</strong>
      </div>
      <p className={styles.notaAdvertencia}>{error.mensaje}</p>
      <p className={styles.notaAdvertencia}>
        Este problema no afecta a una fila o columna concreta: revisa la fila de cabecera o sube otro archivo.
      </p>
      <div className={styles.grupoProblemaAcciones}>
        {onVolverAColumnas && (
          <button type="button" className="btn btnSecondary" disabled={disabled} onClick={onVolverAColumnas}>
            Volver a columnas
          </button>
        )}
        <button type="button" className="btn btnSecondary" disabled={disabled} onClick={onSustituirArchivo}>
          Sustituir archivo
        </button>
      </div>
    </div>
  )
}
