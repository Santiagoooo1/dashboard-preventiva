import type { EstadoPaso, PasoProgreso } from '../../utils/importacionGuiada/orquestador'
import styles from './ImportacionGuiada.module.css'

const ICONO: Record<EstadoPaso, { clase: string; simbolo: string }> = {
  pendiente: { clase: styles.iconoPendiente, simbolo: '' },
  'en-curso': { clase: styles.iconoEnCurso, simbolo: '…' },
  correcto: { clase: styles.iconoCorrecto, simbolo: '✓' },
  error: { clase: styles.iconoError, simbolo: '✕' },
}

interface ImportProgressPanelProps {
  pasos: PasoProgreso[]
}

export function ImportProgressPanel({ pasos }: ImportProgressPanelProps) {
  return (
    <ul className={styles.listaProgreso}>
      {pasos.map((paso) => {
        const icono = ICONO[paso.estado]
        return (
          <li key={paso.clave} className={styles.itemProgreso}>
            <span className={`${styles.iconoProgreso} ${icono.clase}`}>{icono.simbolo}</span>
            <span>{paso.etiqueta}</span>
            {paso.estado === 'error' && paso.mensaje && (
              <span className={styles.mensajePaso}>— {paso.mensaje}</span>
            )}
          </li>
        )
      })}
    </ul>
  )
}
