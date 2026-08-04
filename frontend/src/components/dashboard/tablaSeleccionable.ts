import type { DataTableFilaInteractiva } from '../DataTable'
import type { SeleccionChart } from './BarChartWidget'
import { formatearEtiquetaCategoria } from './camposFiltroDashboard'
import styles from './Charts.module.css'

/**
 * Adaptador para tablas AGREGADAS por una categoría (comparativa,
 * distribución): convierte la interacción genérica de gráfico en la que espera
 * DataTable, de modo que pulsar una fila produzca exactamente el mismo filtro
 * que pulsar la barra equivalente.
 *
 * Solo se aplica a filas cuya `etiqueta` es el valor técnico de una categoría.
 * Devuelve `undefined` cuando el widget no es seleccionable, y entonces la
 * tabla se renderiza inerte, como siempre.
 */
export function filaInteractivaDeCategoria<T extends { etiqueta: string }>(
  seleccion: SeleccionChart | undefined,
): DataTableFilaInteractiva<T> | undefined {
  if (!seleccion) return undefined

  return {
    // `etiqueta` llega del backend sin traducir: es el valor técnico que hay
    // que enviar en el filtro. La versión legible se calcula aparte.
    getValorSeleccion: (row) =>
      row.etiqueta
        ? { valorOriginal: row.etiqueta, etiquetaVisible: formatearEtiquetaCategoria(row.etiqueta) }
        : null,
    valorSeleccionado: seleccion.valorSeleccionado,
    etiquetaCampo: seleccion.etiquetaCampo,
    onSeleccionar: seleccion.onSeleccionar,
    claseFila: styles.filaSeleccionable,
    claseFilaActiva: styles.filaSeleccionada,
  }
}
