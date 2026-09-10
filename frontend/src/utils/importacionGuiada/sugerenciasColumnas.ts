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
  /**
   * Concepto clínico que el backend reconoció en la cabecera, si lo reconoció.
   *
   * Se conserva aunque `codigoInterno` cambie después (por desambiguación o
   * por pasar a ser la fecha principal): es lo que permite detectar que dos
   * columnas distintas del mismo archivo describen el mismo campo.
   */
  codigoCanonico?: string
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

// Palabras que, por sí solas, indican Sí/No. "reingreso" está aquí, pero solo
// se evalúa tras descartar "fecha reingreso" (la palabra "fecha" manda).
const PALABRAS_BOOLEANO = ['exitus', 'fallecido', 'mortalidad', 'reingreso', 'complicacion', 'infeccion', 'ilq']

// Columnas que casi siempre son texto libre no obligatorio (identificadores
// personales, microbiología, comentarios): se resuelven antes que cualquier
// otra heurística para que no las capture un cruce accidental de palabras.
const PALABRAS_TEXTO_LIBRE = [
  'nombre',
  'microorganismo',
  'resistencia',
  'cultivo',
  'muestra',
  'comentarios',
  'comentario',
]

export function sugerirTipoDato(nombre: string): TipoDato {
  const n = normalizarTexto(nombre)
  // FECHA solo si aparece claramente "fecha" o "date". Palabras como "cirugía",
  // "alta" o "ingreso" por sí solas ya no fuerzan FECHA (causaban falsos FECHA).
  if (contiene(n, ['fecha', 'date'])) return 'FECHA'
  if (contiene(n, PALABRAS_TEXTO_LIBRE)) return 'TEXTO'
  // Dinero/proporción antes que conteo: "coste total" es DECIMAL, no ENTERO.
  if (contiene(n, ['coste', 'importe', 'precio', 'porcentaje', 'tasa', 'ratio'])) return 'DECIMAL'
  // Sí/No antes que entero: "exitus … 30 días" es BOOLEANO, no ENTERO por "días".
  if (contiene(n, PALABRAS_BOOLEANO)) return 'BOOLEANO'
  if (contiene(n, ['edad', 'dias', 'estancia', 'minutos', 'duracion', 'numero', 'cantidad', 'total'])) return 'ENTERO'
  return 'TEXTO'
}

export function sugerirRolClinico(nombre: string): RolClinico {
  const n = normalizarTexto(nombre)
  if (n === 'nhc' || n === 'hc' || contiene(n, ['historia', 'paciente'])) return 'paciente'
  // Igual que en el tipo: rol fecha solo con "fecha"/"date".
  if (contiene(n, ['fecha', 'date'])) return 'fecha'
  // Nombre/microbiología/comentarios: texto libre, nunca obligatorio ni paciente.
  if (contiene(n, PALABRAS_TEXTO_LIBRE)) return 'texto'
  if (contiene(n, ['servicio'])) return 'servicio'
  if (contiene(n, ['diagnostico'])) return 'diagnostico'
  if (contiene(n, ['procedimiento'])) return 'procedimiento'
  if (contiene(n, ['edad'])) return 'edad'
  if (contiene(n, ['sexo'])) return 'sexo'
  if (contiene(n, ['gravedad', 'categoria'])) return 'categoria'
  if (contiene(n, PALABRAS_BOOLEANO)) return 'booleano'
  if (contiene(n, ['dias', 'estancia', 'minutos', 'duracion', 'numero', 'cantidad', 'total', 'coste', 'importe', 'tasa']))
    return 'numero'
  return 'texto'
}

export const CODIGO_CANONICO: Partial<Record<RolClinico, string>> = {
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

/**
 * Construye la configuración inicial de una columna a partir de su nombre.
 * Ninguna fecha recibe todavía el código canónico `fechaEvento` aquí: todas
 * arrancan con un código propio derivado de su nombre (fechaIngreso,
 * fechaCirugia...). Cuál de ellas es la "fecha principal" se decide después de
 * ver todas las columnas del archivo (ver `camposClave.ts`), no por ser la
 * primera en aparecer.
 */
export function construirColumnaConfigurada(indiceColumna: number, nombreOriginal: string): ColumnaConfigurada {
  const rol = sugerirRolClinico(nombreOriginal)
  const tipoDato = rol === 'fecha' ? 'FECHA' : sugerirTipoDato(nombreOriginal)
  const codigoInterno = CODIGO_CANONICO[rol] ?? sugerirCodigoInterno(nombreOriginal)

  return {
    indiceColumna,
    nombreOriginal,
    usar: rol !== 'ignorar',
    nombreVisible: sugerirNombreVisible(nombreOriginal),
    codigoInterno,
    tipoDato,
    rol,
    // Las fechas empiezan como no comunes: solo la que se promocione a
    // fechaEvento pasará a serlo (ver establecerFechaPrincipal).
    esComun: rol === 'fecha' ? false : esCampoComunDesdeRol(rol),
    // Solo el identificador de paciente se exige de entrada; la fecha
    // principal se marca obligatoria al fijarla (ver camposClave.ts).
    obligatorio: rol === 'paciente',
  }
}

const NOMBRES_BANDERA_SEXO = ['hombre', 'mujer', 'varon']

/**
 * Si el archivo ya tiene una columna de rol "sexo", las columnas derivadas
 * ("Hombre", "Mujer", "Varón" como banderas 0/1) sobran y se sugieren ignorar.
 */
function ignorarBanderasDeSexoRedundantes(columnas: ColumnaConfigurada[]): ColumnaConfigurada[] {
  const haySexo = columnas.some((c) => c.rol === 'sexo')
  if (!haySexo) return columnas
  return columnas.map((c) => {
    if (!NOMBRES_BANDERA_SEXO.includes(normalizarTexto(c.nombreOriginal))) return c
    return { ...c, rol: 'ignorar', usar: false, esComun: false, obligatorio: false }
  })
}

/**
 * Aplica las sugerencias a todas las columnas detectadas, en orden, para que la
 * regla de "primera fecha → fechaEvento" funcione, y desambigua códigos repetidos.
 */
/**
 * Columna reconocida por el backend: ya sabemos qué campo es y de qué tipo.
 * Los tres campos van juntos o no van.
 */
export interface ColumnaCanonica {
  codigoCanonico?: string | null
  tipoDatoCanonico?: TipoDato | null
  esComunCanonico?: boolean | null
  etiquetaCanonica?: string | null
  origenReconocimiento?: string | null
}

/**
 * Aplica lo que el backend ya sabe de la columna por encima de la heurística.
 *
 * Deducir por el nombre está bien cuando no hay más remedio, pero es
 * exactamente lo que convirtió «LOCALIZACIÓN DE LA INFECCIÓN» en un Sí/No: la
 * palabra «infección» activaba la regla de booleano y se perdían las filas de
 * los pacientes infectados, que son las únicas que traen localización. Si la
 * columna está en el catálogo clínico, no hay nada que adivinar.
 */
function aplicarCanonico(columna: ColumnaConfigurada, canonica: ColumnaCanonica): ColumnaConfigurada {
  if (!canonica.codigoCanonico || !canonica.tipoDatoCanonico) return columna
  return {
    ...columna,
    codigoInterno: canonica.codigoCanonico,
    codigoCanonico: canonica.codigoCanonico,
    tipoDato: canonica.tipoDatoCanonico,
    esComun: canonica.esComunCanonico ?? columna.esComun,
    // Solo se sustituye el nombre visible cuando el del archivo es una sigla
    // ilegible («ILQ»). Si el hospital escribió algo con sentido, se respeta:
    // es el nombre por el que su gente reconoce la columna.
    nombreVisible: canonica.etiquetaCanonica && columna.nombreOriginal.trim().length <= 4
      ? canonica.etiquetaCanonica
      : columna.nombreVisible,
    // El rol guía la UI (qué controles se ofrecen); se alinea con el tipo real
    // para que un campo de texto no siga ofreciendo opciones de Sí/No.
    rol: rolDesdeTipoCanonico(canonica.tipoDatoCanonico),
    usar: true,
  }
}

function rolDesdeTipoCanonico(tipoDato: TipoDato): RolClinico {
  if (tipoDato === 'FECHA') return 'fecha'
  if (tipoDato === 'BOOLEANO') return 'booleano'
  if (tipoDato === 'ENTERO' || tipoDato === 'DECIMAL') return 'numero'
  return 'texto'
}

export function sugerirColumnas(
  cabeceras: ({ indiceColumna: number; nombreOriginal: string } & ColumnaCanonica)[],
): ColumnaConfigurada[] {
  const columnas = cabeceras.map((c) =>
    aplicarCanonico(construirColumnaConfigurada(c.indiceColumna, c.nombreOriginal), c),
  )
  return desambiguarCodigos(ignorarBanderasDeSexoRedundantes(columnas))
}

/**
 * Columnas distintas del archivo que describen el mismo campo clínico.
 *
 * Pasa de verdad: un Excel con «LOCALIZACIÓN ILQ» y «LOCALIZACIÓN DE LA
 * INFECCIÓN» tiene dos veces lo mismo. `desambiguarCodigos` las salvaría
 * renombrando la segunda a `localizacionInfeccion2`, pero eso deja dos campos
 * medio vacíos y ningún aviso: el usuario descubre el problema al ver un
 * gráfico incompleto. Mejor decirlo antes de importar y que decida él.
 *
 * @returns código canónico → columnas que lo reclaman (solo cuando hay más de una)
 */
export function detectarConceptosDuplicados(
  columnas: ColumnaConfigurada[],
): Map<string, ColumnaConfigurada[]> {
  const porConcepto = new Map<string, ColumnaConfigurada[]>()
  for (const columna of columnas) {
    if (!columna.usar || !columna.codigoCanonico) continue
    const grupo = porConcepto.get(columna.codigoCanonico) ?? []
    grupo.push(columna)
    porConcepto.set(columna.codigoCanonico, grupo)
  }
  return new Map([...porConcepto].filter(([, grupo]) => grupo.length > 1))
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
