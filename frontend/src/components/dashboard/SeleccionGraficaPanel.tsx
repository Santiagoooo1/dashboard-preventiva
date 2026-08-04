import type { SeleccionGrafica } from './seleccionGrafica'
import styles from './SeleccionGraficaPanel.module.css'

interface SeleccionGraficaPanelProps {
  seleccion: SeleccionGrafica | null
  cargando: boolean
  error: string | null
  onQuitar: () => void
  onReintentar: () => void
}

/**
 * Zona de la selección gráfica (nivel 3). Vive entre el contexto global y la
 * cuadrícula, y solo se renderiza cuando hay selección: en turquesa, para que
 * no se confunda ni con el cobalto del contexto global ni con el azul cielo de
 * la exploración local de cada widget.
 */
export function SeleccionGraficaPanel({
  seleccion,
  cargando,
  error,
  onQuitar,
  onReintentar,
}: SeleccionGraficaPanelProps) {
  if (!seleccion) return null

  return (
    <section className={styles.panel} aria-label="Selección desde gráfico">
      <div className={styles.cabecera}>
        <span className={styles.titulo}>Selección desde gráfico</span>
        <span className={styles.ayuda}>Esta selección recalcula los demás indicadores.</span>
      </div>

      <div className={styles.acciones}>
        <button
          type="button"
          className={styles.chip}
          onClick={onQuitar}
          aria-label={`Quitar selección ${seleccion.etiquetaCampo}: ${seleccion.etiquetaVisible}`}
        >
          {seleccion.etiquetaCampo}: {seleccion.etiquetaVisible} <span aria-hidden="true">×</span>
        </button>

        <button type="button" className={styles.limpiar} onClick={onQuitar}>
          Limpiar selección
        </button>

        {cargando && (
          <span className={styles.cargando} role="status">
            Actualizando indicadores…
          </span>
        )}
      </div>

      {/* El dashboard base sigue intacto detrás: desde aquí solo se puede
          reintentar el cruce o descartar la selección. */}
      {error && (
        <div className={styles.error} role="alert">
          <span className={styles.errorTexto}>{error}</span>
          <button type="button" className={styles.limpiar} onClick={onReintentar}>
            Reintentar
          </button>
          <button type="button" className={styles.limpiar} onClick={onQuitar}>
            Quitar selección
          </button>
        </div>
      )}
    </section>
  )
}
