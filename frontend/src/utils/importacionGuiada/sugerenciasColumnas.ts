import type { TipoDato } from '../../api/types'

// Roles clínicos que el asistente ofrece al usuario. Deliberadamente en
// lenguaje médico, sin exponer conceptos internos como "campo común".
export type RolClinico =
  | 'paciente'
  | 'fecha'
  | 'servicio'
  | 'diagnostico'
  | 'procedimiento'
  | 'edad'
  | 'sexo'
  | 'categoria'
  | 'numero'
  | 'booleano'
  | 'texto'
  | 'ignorar'

export const ROLES_CLINICOS: { valor: RolClinico; etiqueta: string }[] = [
  { valor: 'paciente', etiqueta: 'Paciente' },
  { valor: 'fecha', etiqueta: 'Fecha' },
  { valor: 'servicio', etiqueta: 'Servicio' },
  { valor: 'diagnostico', etiqueta: 'Diagnóstico' },
  { valor: 'procedimiento', etiqueta: 'Procedimiento' },
  { valor: 'edad', etiqueta: 'Edad' },
  { valor: 'sexo', etiqueta: 'Sexo' },
  { valor: 'categoria', etiqueta: 'Categoría' },
  { valor: 'numero', etiqueta: 'Número' },
  { valor: 'booleano', etiqueta: 'Sí / No' },
  { valor: 'texto', etiqueta: 'Texto' },
  { valor: 'ignorar', etiqueta: 'No usar' },
]

export interface ColumnaConfigurada {
  indiceColumna: number
  nombreOriginal: string
  usar: boolean
  nombreVisible: string
  codigoInterno: string
  tipoDato: TipoDato
  rol: RolClinico
  esComun: boolean
  obligatorio: boolean
}

/** Quita acentos, colapsa espacios y pasa a minúsculas para poder comparar. */
export function normalizarTexto(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}

// Coincidencia por palabra completa: evita falsos positivos por subcadena
// ("gravedad" no debe activar "edad", "reingreso" no debe activar "ingreso").
function contiene(normal: string, palabras: string[]): boolean {
  return palabras.some((p) => new RegExp(`(^|\\W)${p}(\\W|$)`).test(normal))
}

/** Convierte un nombre libre en camelCase válido: solo letras/números/_. */
export function sugerirCodigoInterno(nombre: string): string {
  const limpio = nombre
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/[^A-Za-z0-9\s]/g, ' ')
    .trim()
  const palabras = limpio.split(/\s+/).filter(Boolean)
  if (palabras.length === 0) return 'campo'
  return palabras
    .map((palabra, i) =>
      i === 0
        ? palabra.toLowerCase()
        : palabra.charAt(0).toUpperCase() + palabra.slice(1).toLowerCase(),
    )
    .join('')
}

/** Código de dataset en MAYUSCULAS_CON_GUION_BAJO a partir del nombre de archivo. */
export function sugerirCodigoDataset(nombre: string): string {
  const limpio = nombre
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/[^A-Za-z0-9\s_]/g, ' ')
    .trim()
  const codigo = limpio
    .split(/[\s_]+/)
    .filter(Boolean)
    .join('_')
    .toUpperCase()
  return codigo || 'DATASET'
}

/** Nombre visible legible: capitaliza y respeta el original salvo limpieza mínima. */
export function sugerirNombreVisible(nombre: string): string {
  const limpio = nombre.replace(/[_]+/g, ' ').replace(/\s+/g, ' ').trim()
  if (!limpio) return nombre
  return limpio.charAt(0).toUpperCase() + limpio.slice(1)
}

/** Nombre de dashboard a partir del nombre de archivo (sin extensión). */
export function sugerirNombreDataset(nombreArchivo: string): string {
  const sinExtension = nombreArchivo.replace(/\.[^.]+$/, '')
  const palabras = sinExtension
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean)
  if (palabras.length === 0) return 'Nuevo dashboard'
  return palabras.map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ')
}

export function sugerirTipoDato(nombre: string): TipoDato {
  const n = normalizarTexto(nombre)
  if (contiene(n, ['fecha', 'date', 'ingreso', 'alta', 'cirugia'])) return 'FECHA'
  // Los términos de dinero/proporción van antes que los de conteo: "coste total"
  // es DECIMAL (manda "coste"), no ENTERO por "total".
  if (contiene(n, ['coste', 'importe', 'precio', 'porcentaje', 'tasa', 'ratio'])) return 'DECIMAL'
  if (contiene(n, ['edad', 'dias', 'estancia', 'numero', 'cantidad', 'total'])) return 'ENTERO'
  if (contiene(n, ['fallecido', 'reingreso', 'complicacion', 'infeccion', 'ilq', 'mortalidad'])) return 'BOOLEANO'
  if (contiene(n, ['servicio', 'diagnostico', 'procedimiento', 'sexo', 'gravedad', 'categoria'])) return 'TEXTO'
  return 'TEXTO'
}

export function sugerirRolClinico(nombre: string): RolClinico {
  const n = normalizarTexto(nombre)
  if (n === 'nhc' || n === 'hc' || contiene(n, ['historia', 'paciente'])) return 'paciente'
  if (contiene(n, ['fecha', 'ingreso', 'alta', 'cirugia'])) return 'fecha'
  if (contiene(n, ['servicio'])) return 'servicio'
  if (contiene(n, ['diagnostico'])) return 'diagnostico'
  if (contiene(n, ['procedimiento'])) return 'procedimiento'
  if (contiene(n, ['edad'])) return 'edad'
  if (contiene(n, ['sexo'])) return 'sexo'
  if (contiene(n, ['gravedad', 'categoria'])) return 'categoria'
  if (contiene(n, ['fallecido', 'reingreso', 'complicacion', 'mortalidad', 'infeccion', 'ilq'])) return 'booleano'
  if (contiene(n, ['dias', 'estancia', 'numero', 'cantidad', 'total', 'coste', 'importe', 'tasa'])) return 'numero'
  return 'texto'
}

const CODIGO_CANONICO: Partial<Record<RolClinico, string>> = {
  paciente: 'pacienteCodigo',
  servicio: 'servicio',
  diagnostico: 'diagnostico',
  procedimiento: 'procedimiento',
  edad: 'edad',
  sexo: 'sexo',
}

const ROLES_COMUNES: RolClinico[] = ['paciente', 'fecha', 'servicio', 'diagnostico', 'procedimiento', 'edad', 'sexo']

export function esCampoComunDesdeRol(rol: RolClinico): boolean {
  return ROLES_COMUNES.includes(rol)
}

interface ContextoConstruccion {
  /** Se activa cuando ya se ha asignado la primera columna con rol fecha. */
  primeraFechaUsada: boolean
}

/**
 * Construye la configuración inicial de una columna a partir de su nombre.
 * La primera fecha del archivo recibe el código canónico `fechaEvento`
 * (facilita la serie temporal de 6.8B); las demás derivan del nombre.
 */
export function construirColumnaConfigurada(
  indiceColumna: number,
  nombreOriginal: string,
  contexto: ContextoConstruccion,
): ColumnaConfigurada {
  const rol = sugerirRolClinico(nombreOriginal)
  const tipoDato = rol === 'fecha' ? 'FECHA' : sugerirTipoDato(nombreOriginal)

  let codigoInterno: string
  if (rol === 'fecha') {
    codigoInterno = contexto.primeraFechaUsada ? sugerirCodigoInterno(nombreOriginal) : 'fechaEvento'
    contexto.primeraFechaUsada = true
  } else if (CODIGO_CANONICO[rol]) {
    codigoInterno = CODIGO_CANONICO[rol] as string
  } else {
    codigoInterno = sugerirCodigoInterno(nombreOriginal)
  }

  // Solo la fecha principal (fechaEvento) es común; las fechas adicionales son
  // campos normales aunque su rol siga siendo "fecha".
  const esComun = rol === 'fecha' ? codigoInterno === 'fechaEvento' : esCampoComunDesdeRol(rol)

  return {
    indiceColumna,
    nombreOriginal,
    usar: rol !== 'ignorar',
    nombreVisible: sugerirNombreVisible(nombreOriginal),
    codigoInterno,
    tipoDato,
    rol,
    esComun,
    // El identificador de paciente y la fecha principal son los dos campos que
    // conviene exigir; el resto queda opcional para no bloquear la importación.
    obligatorio: rol === 'paciente' || codigoInterno === 'fechaEvento',
  }
}

/**
 * Aplica las sugerencias a todas las columnas detectadas, en orden, para que la
 * regla de "primera fecha → fechaEvento" funcione, y desambigua códigos repetidos.
 */
export function sugerirColumnas(cabeceras: { indiceColumna: number; nombreOriginal: string }[]): ColumnaConfigurada[] {
  const contexto: ContextoConstruccion = { primeraFechaUsada: false }
  const columnas = cabeceras.map((c) => construirColumnaConfigurada(c.indiceColumna, c.nombreOriginal, contexto))
  return desambiguarCodigos(columnas)
}

/** Garantiza códigos únicos entre las columnas usadas (el backend los exige únicos). */
export function desambiguarCodigos(columnas: ColumnaConfigurada[]): ColumnaConfigurada[] {
  const vistos = new Map<string, number>()
  return columnas.map((columna) => {
    if (!columna.usar) return columna
    const base = columna.codigoInterno || 'campo'
    const cuenta = vistos.get(base) ?? 0
    vistos.set(base, cuenta + 1)
    return cuenta === 0 ? columna : { ...columna, codigoInterno: `${base}${cuenta + 1}` }
  })
}
