// Heurística por nombre de columna (igual espíritu que TextNormalizer en el
// backend: sin acentos, mayúsculas) para mostrar identificadores útiles de una
// fila de la copia de trabajo (HC, servicio, procedimiento...) en vez de solo
// su número. Los nombres de columna son libres por plantilla, así que esto es
// una heurística de UI, no una fuente de verdad clínica.

interface PatronIdentificador {
  etiqueta: string
  patrones: RegExp[]
}

const PATRONES: PatronIdentificador[] = [
  { etiqueta: 'HC', patrones: [/^HC$/, /HISTORIA CLIN/] },
  { etiqueta: 'Nombre', patrones: [/^NOMBRE/, /PACIENTE/] },
  { etiqueta: 'Servicio', patrones: [/SERVICIO/] },
  { etiqueta: 'Procedimiento', patrones: [/PROCEDIMIENTO/, /INTERVENCION/] },
  { etiqueta: 'Fecha cirugía', patrones: [/FECHA.*CIRUG/] },
  { etiqueta: 'CIE-10', patrones: [/CIE.?10/, /DIAGNOSTIC/] },
]

// ̀-ͯ cubre las marcas diacríticas combinantes que deja NFD al
// separar una letra acentuada (p. ej. "í" -> "i" + U+0301).
export function normalizarNombreColumna(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .trim()
}

export interface IdentificadorFila {
  etiqueta: string
  valor: string
}

/**
 * Devuelve, para las columnas reconocidas (HC, nombre, servicio...), su valor
 * efectivo: el corregido si existe, si no el original. Omite las que no
 * existan en la fila o cuyo valor efectivo esté vacío.
 */
export function identificadoresFila(
  valoresOriginales: Record<string, string | null>,
  valoresCorregidos: Record<string, string | null>,
): IdentificadorFila[] {
  const columnas = Object.keys(valoresOriginales)
  const resultado: IdentificadorFila[] = []

  for (const { etiqueta, patrones } of PATRONES) {
    const columna = columnas.find((c) => patrones.some((p) => p.test(normalizarNombreColumna(c))))
    if (!columna) continue

    const corregido = valoresCorregidos[columna]
    const valorEfectivo =
      corregido !== undefined && corregido !== null && corregido !== '' ? corregido : valoresOriginales[columna]

    if (valorEfectivo && valorEfectivo.trim() !== '') {
      resultado.push({ etiqueta, valor: valorEfectivo.trim() })
    }
  }

  return resultado
}

// Además de los identificadores mostrados en las tarjetas de fila, sexo y
// edad también se consideran críticos a la hora de confirmar un relleno en
// bloque (no se muestran como identificador porque no ayudan a reconocer la
// fila tanto como HC/servicio/procedimiento).
const PATRONES_CRITICOS_ADICIONALES: RegExp[] = [/^SEXO$/, /^EDAD/]

/**
 * true si el nombre de columna parece un dato clínico identificador o crítico
 * (HC, nombre, servicio, procedimiento, fecha de cirugía, CIE-10, sexo,
 * edad). Se usa para pedir una confirmación extra antes de rellenar ese tipo
 * de columnas en bloque: no se debe "inventar" un dato clínico crítico sin
 * que el usuario lo confirme explícitamente.
 */
export function esColumnaClinicaCritica(nombreColumna: string): boolean {
  const normalizado = normalizarNombreColumna(nombreColumna)
  return (
    PATRONES.some((p) => p.patrones.some((patron) => patron.test(normalizado))) ||
    PATRONES_CRITICOS_ADICIONALES.some((patron) => patron.test(normalizado))
  )
}
