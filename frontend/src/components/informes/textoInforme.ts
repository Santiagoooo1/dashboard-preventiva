/**
 * Textos compartidos entre la cabecera/pie en pantalla y los de impresión
 * (extraídos en la Fase 6.9R.2).
 *
 * <p>La hoja los pinta como elementos HTML y la página de impresión los
 * incrusta en las margin boxes de `@page`. Son el mismo dato en dos soportes:
 * si cada lado los formatease por su cuenta, el PDF y la pantalla acabarían
 * enseñando fechas con formatos distintos.
 */

export function formatearFecha(iso: string | null): string {
  if (!iso) return '—'
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) return '—'
  const dd = String(fecha.getDate()).padStart(2, '0')
  const mm = String(fecha.getMonth() + 1).padStart(2, '0')
  const hh = String(fecha.getHours()).padStart(2, '0')
  const mi = String(fecha.getMinutes()).padStart(2, '0')
  return `${dd}/${mm}/${fecha.getFullYear()} ${hh}:${mi}`
}

/**
 * Pie legible con muchos datasets.
 *
 * <p>Un hospital puede comparar seis años y seis nombres largos no caben en una
 * línea de pie. Se enseñan los primeros y se cuenta el resto en vez de recortar
 * con puntos suspensivos, que escondería cuántos faltan.
 */
export function resumirDatasets(nombres: string[], maximo = 3): string {
  if (nombres.length <= maximo) return nombres.join(' · ')
  const resto = nombres.length - maximo
  return `${nombres.slice(0, maximo).join(' · ')} y ${resto} más`
}

/**
 * Escapa un texto para meterlo dentro de `content: "..."` en CSS.
 *
 * <p>Hace falta porque Chrome no implementa `string-set`/`string()`: la única
 * forma de llevar el título a la cabecera física es incrustarlo como literal.
 * Sin escapar, un título con comillas rompería la hoja de estilos entera y el
 * PDF saldría sin cabecera ni numeración.
 */
export function comillasCss(texto: string): string {
  const escapado = texto
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    // Un salto de línea dentro de una cadena CSS la invalida.
    .replace(/[\r\n]+/g, ' ')
  return `"${escapado}"`
}
