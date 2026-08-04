import type { ColumnaSubconjuntoDto } from '../../api/types'
import { formatNumber } from '../../utils/formatters'

/**
 * Formateo de celdas del detalle del subconjunto (Fase 6.9H.3).
 *
 * El backend envía valores técnicos y la metadata de cada columna; aquí se
 * decide cómo mostrarlos. Los valores originales no se alteran: esto es solo
 * presentación.
 */

/** "2026-01-31" → "31/01/2026", partiendo la cadena para no pasar por Date (UTC). */
export function formatearFecha(iso: string): string {
  const [anio, mes, dia] = iso.split('-')
  return dia && mes && anio ? `${dia}/${mes}/${anio}` : iso
}

export const SIN_DATO = 'Sin dato'

/**
 * Valor de celda listo para pintar. Nunca devuelve "null", "undefined",
 * "true" ni "false": un hueco es "Sin dato" y un booleano es Sí/No.
 */
export function formatearCelda(valor: unknown, columna: ColumnaSubconjuntoDto): string {
  if (valor === null || valor === undefined || valor === '') return SIN_DATO

  if (columna.tipoDato === 'BOOLEANO') {
    if (valor === true || valor === 'true') return 'Sí'
    if (valor === false || valor === 'false') return 'No'
    // "Varios" (agregación por paciente con valores discrepantes) llega tal cual.
    return String(valor)
  }

  if (columna.tipoDato === 'FECHA' && typeof valor === 'string') {
    return formatearFecha(valor)
  }

  if ((columna.tipoDato === 'ENTERO' || columna.tipoDato === 'DECIMAL') && typeof valor === 'number') {
    return formatNumber(valor)
  }

  return String(valor)
}

/** Etiqueta legible de un valor de categoría en el perfil (mismo criterio que las celdas). */
export function formatearValorCategoria(valor: string, tipoDato: string): string {
  if (tipoDato === 'BOOLEANO') {
    if (valor === 'true') return 'Sí'
    if (valor === 'false') return 'No'
  }
  return valor
}

/** Las columnas numéricas se alinean a la derecha para poder compararlas de un vistazo. */
export function esNumerica(columna: ColumnaSubconjuntoDto): boolean {
  return columna.tipoDato === 'ENTERO' || columna.tipoDato === 'DECIMAL'
}
