import type { ErrorFilaImportacionGenericaDto, TipoDato } from '../../api/types'
import type { ColumnaConfigurada } from './sugerenciasColumnas'
import type { ClavePaso } from './orquestador'

export interface ProblemaColumna {
  estado: 'error' | 'advertencia'
  totalErrores: number
  problemaLegible: string
  valoresEjemplo: string[]
  correccion: CorreccionSugerida
  /** true si la columna es el identificador de paciente o la fecha principal. */
  esCampoClaveCritico: boolean
  /** true si el problema predominante es de valores vacíos (VALOR_OBLIGATORIO_VACIO). */
  esValorVacio: boolean
}

export interface GrupoErrorColumna {
  nombreColumna: string
  totalErrores: number
  severidadMaxima: 'ERROR' | 'ADVERTENCIA'
  tipoErrorPredominante: string
  valoresEjemplo: string[]
  /** Mensaje del backend para un error representativo del tipo predominante. */
  mensajeEjemplo: string
  /** numeroFila de un error representativo del tipo predominante (puede ser una fila de cabecera, no clínica). */
  numeroFilaEjemplo: number | null
}

export interface CorreccionSugerida {
  tipoSugerido: TipoDato | null
  marcarNoObligatorio: boolean
  texto: string
}

const VALORES_BOOLEANOS = new Set([
  'SI', 'SÍ', 'S', 'NO', 'N', 'TRUE', 'FALSE', 'VERDADERO', 'FALSO', '1', '0', 'X',
  'POSITIVO', 'NEGATIVO',
])

// Traducción de los códigos técnicos del backend a lenguaje comprensible.
const PROBLEMAS: Record<string, string> = {
  FORMATO_FECHA_INVALIDO: 'Se esperaba una fecha',
  FORMATO_NUMERO_INVALIDO: 'Se esperaba un número',
  FORMATO_BOOLEANO_INVALIDO: 'Se esperaba Sí/No',
  VALOR_OBLIGATORIO_VACIO: 'Faltan valores obligatorios',
  VALOR_NO_RECONOCIDO: 'Valores no reconocidos',
  COLUMNA_OBLIGATORIA_FALTANTE: 'Falta una columna obligatoria',
}

export function problemaLegible(tipoError: string): string {
  return PROBLEMAS[tipoError] ?? 'Valores no válidos'
}

/**
 * Igual que `problemaLegible`, pero para VALOR_OBLIGATORIO_VACIO distingue si
 * la columna afectada es un campo clave (identificador de paciente, fecha
 * principal) o una columna secundaria, para no mostrar siempre el mismo
 * mensaje genérico ("Faltan valores obligatorios") sin importar la columna.
 */
export function problemaLegibleParaColumna(tipoError: string, columna: ColumnaConfigurada): string {
  if (tipoError === 'VALOR_OBLIGATORIO_VACIO') {
    if (columna.codigoInterno === 'pacienteCodigo') return 'Hay filas sin identificador de paciente.'
    if (columna.codigoInterno === 'fechaEvento') return 'Hay filas sin fecha principal.'
    return 'Esta columna tiene valores vacíos.'
  }
  return problemaLegible(tipoError)
}

/**
 * Separa errores "globales" (sin columna asociada, p. ej. SIN_FILAS_CLINICAS:
 * el archivo entero no tiene filas clínicas reconocibles) de los que sí
 * describen un problema de una columna concreta. Un error global no debe
 * agruparse como si fuera una columna llamada "Columna desconocida": no hay
 * fila ni columna real que corregir, es un problema del archivo en su
 * conjunto.
 */
export function separarErroresGlobales(errores: ErrorFilaImportacionGenericaDto[]): {
  globales: ErrorFilaImportacionGenericaDto[]
  porColumna: ErrorFilaImportacionGenericaDto[]
} {
  const globales: ErrorFilaImportacionGenericaDto[] = []
  const porColumna: ErrorFilaImportacionGenericaDto[] = []
  for (const error of errores) {
    if (error.nombreColumna === null || error.nombreColumna.trim() === '') {
      globales.push(error)
    } else {
      porColumna.push(error)
    }
  }
  return { globales, porColumna }
}

/** Agrupa la lista de errores de fila por columna, con muestra de valores. */
export function agruparErroresPorColumna(errores: ErrorFilaImportacionGenericaDto[]): GrupoErrorColumna[] {
  const mapa = new Map<
    string,
    {
      total: number
      severidades: Set<string>
      tipos: Map<string, number>
      valores: Set<string>
      mensajesPorTipo: Map<string, string>
      numerosPorTipo: Map<string, number | null>
    }
  >()

  for (const error of errores) {
    const columna = (error.nombreColumna ?? 'Columna desconocida').trim()
    if (!mapa.has(columna)) {
      mapa.set(columna, {
        total: 0,
        severidades: new Set(),
        tipos: new Map(),
        valores: new Set(),
        mensajesPorTipo: new Map(),
        numerosPorTipo: new Map(),
      })
    }
    const grupo = mapa.get(columna)!
    grupo.total += 1
    grupo.severidades.add(error.severidad)
    grupo.tipos.set(error.tipoError, (grupo.tipos.get(error.tipoError) ?? 0) + 1)
    if (!grupo.mensajesPorTipo.has(error.tipoError)) grupo.mensajesPorTipo.set(error.tipoError, error.mensaje)
    if (!grupo.numerosPorTipo.has(error.tipoError)) grupo.numerosPorTipo.set(error.tipoError, error.numeroFila)
    if (error.valorOriginal && error.valorOriginal.trim() !== '' && grupo.valores.size < 3) {
      grupo.valores.add(error.valorOriginal.trim())
    }
  }

  return [...mapa.entries()].map(([nombreColumna, g]) => {
    const tipoErrorPredominante = [...g.tipos.entries()].sort((a, b) => b[1] - a[1])[0][0]
    return {
      nombreColumna,
      totalErrores: g.total,
      severidadMaxima: g.severidades.has('ERROR') ? 'ERROR' : 'ADVERTENCIA',
      tipoErrorPredominante,
      valoresEjemplo: [...g.valores],
      mensajeEjemplo: g.mensajesPorTipo.get(tipoErrorPredominante) ?? '',
      numeroFilaEjemplo: g.numerosPorTipo.get(tipoErrorPredominante) ?? null,
    }
  })
}

/** numeroFila (sin duplicados, ordenados) de los errores de una columna y severidad concretas. */
export function numerosFilaConError(
  errores: ErrorFilaImportacionGenericaDto[],
  severidad: string,
  nombreColumna: string,
): number[] {
  const numeros = new Set<number>()
  for (const error of errores) {
    if (
      error.severidad === severidad &&
      (error.nombreColumna ?? '').trim() === nombreColumna.trim() &&
      error.numeroFila !== null
    ) {
      numeros.add(error.numeroFila)
    }
  }
  return [...numeros].sort((a, b) => a - b)
}

function todosBooleanos(valores: string[]): boolean {
  return valores.length > 0 && valores.every((v) => VALORES_BOOLEANOS.has(v.toUpperCase()))
}

function todosEnteros(valores: string[]): boolean {
  return valores.length > 0 && valores.every((v) => /^-?\d+$/.test(v.trim()))
}

function todosNumeros(valores: string[]): boolean {
  return valores.length > 0 && valores.every((v) => /^-?\d+([.,]\d+)?$/.test(v.trim()))
}

function pareceTexto(valores: string[]): boolean {
  return valores.some((v) => v.includes('/') || !/^-?\d+([.,]\d+)?$/.test(v.trim()))
}

/**
 * Propone una corrección para un grupo de errores según su tipo predominante y
 * los valores de ejemplo observados. "columna" aporta el tipo/rol actual.
 */
export function sugerirCorreccion(
  grupo: GrupoErrorColumna,
  columna: ColumnaConfigurada | undefined,
): CorreccionSugerida {
  const valores = grupo.valoresEjemplo

  if (grupo.tipoErrorPredominante === 'FORMATO_FECHA_INVALIDO') {
    if (todosBooleanos(valores)) {
      return { tipoSugerido: 'BOOLEANO', marcarNoObligatorio: false, texto: 'Cambiar a Sí/No' }
    }
    if (pareceTexto(valores)) {
      return { tipoSugerido: 'TEXTO', marcarNoObligatorio: false, texto: 'Cambiar a Texto' }
    }
    if (todosEnteros(valores)) {
      return { tipoSugerido: 'ENTERO', marcarNoObligatorio: false, texto: 'Cambiar a Número entero' }
    }
    if (todosNumeros(valores)) {
      return { tipoSugerido: 'DECIMAL', marcarNoObligatorio: false, texto: 'Cambiar a Número decimal' }
    }
    return { tipoSugerido: 'TEXTO', marcarNoObligatorio: false, texto: 'Cambiar a Texto' }
  }

  if (grupo.tipoErrorPredominante === 'FORMATO_NUMERO_INVALIDO') {
    return { tipoSugerido: 'TEXTO', marcarNoObligatorio: false, texto: 'Cambiar a Texto' }
  }

  if (grupo.tipoErrorPredominante === 'FORMATO_BOOLEANO_INVALIDO') {
    return { tipoSugerido: 'TEXTO', marcarNoObligatorio: false, texto: 'Cambiar a Texto' }
  }

  if (grupo.tipoErrorPredominante === 'VALOR_OBLIGATORIO_VACIO') {
    const esClave = columna?.rol === 'paciente' || columna?.codigoInterno === 'fechaEvento'
    if (!esClave) {
      return { tipoSugerido: null, marcarNoObligatorio: true, texto: 'Marcar como no obligatorio' }
    }
    return { tipoSugerido: null, marcarNoObligatorio: false, texto: 'Revisar manualmente' }
  }

  return { tipoSugerido: 'TEXTO', marcarNoObligatorio: false, texto: 'Revisar manualmente (cambiar a Texto es seguro)' }
}

/**
 * Construye, para cada columna con errores, su problema resumido. Una columna
 * ya corregida localmente (el usuario aplicó el cambio sugerido, aunque no se
 * haya revalidado todavía contra el archivo) deja de listarse como problema,
 * para que las acciones inline den sensación de efecto inmediato.
 */
export function construirProblemasPorColumna(
  errores: ErrorFilaImportacionGenericaDto[],
  columnas: ColumnaConfigurada[],
): Map<number, ProblemaColumna> {
  const grupos = agruparErroresPorColumna(errores)
  const mapa = new Map<number, ProblemaColumna>()

  for (const grupo of grupos) {
    const columna = columnas.find((c) => c.nombreOriginal.trim() === grupo.nombreColumna.trim())
    if (!columna || !columna.usar) continue

    const correccion = sugerirCorreccion(grupo, columna)
    const tipoYaCorregido = correccion.tipoSugerido !== null && columna.tipoDato === correccion.tipoSugerido
    const obligatorioYaCorregido = correccion.marcarNoObligatorio && !columna.obligatorio
    if (tipoYaCorregido || obligatorioYaCorregido) continue

    mapa.set(columna.indiceColumna, {
      estado: grupo.severidadMaxima === 'ERROR' ? 'error' : 'advertencia',
      totalErrores: grupo.totalErrores,
      problemaLegible: problemaLegibleParaColumna(grupo.tipoErrorPredominante, columna),
      valoresEjemplo: grupo.valoresEjemplo,
      correccion,
      esCampoClaveCritico: columna.codigoInterno === 'pacienteCodigo' || columna.codigoInterno === 'fechaEvento',
      esValorVacio: grupo.tipoErrorPredominante === 'VALOR_OBLIGATORIO_VACIO',
    })
  }

  return mapa
}

/**
 * Agrupa los errores de fila por índice de columna, sin deduplicar (a
 * diferencia de `agruparErroresPorColumna`): se usa para listar las filas
 * afectadas una a una en "Ver filas afectadas".
 */
export function agruparErroresPorColumnaIndice(
  errores: ErrorFilaImportacionGenericaDto[],
  columnas: ColumnaConfigurada[],
): Map<number, ErrorFilaImportacionGenericaDto[]> {
  const mapa = new Map<number, ErrorFilaImportacionGenericaDto[]>()
  for (const error of errores) {
    const columna = columnas.find((c) => c.nombreOriginal.trim() === (error.nombreColumna ?? '').trim())
    if (!columna) continue
    if (!mapa.has(columna.indiceColumna)) mapa.set(columna.indiceColumna, [])
    mapa.get(columna.indiceColumna)!.push(error)
  }
  return mapa
}

/**
 * Una corrección es "segura" para aplicarse en bloque solo si hay un cambio
 * concreto que hacer y no toca los campos clave (paciente / fecha principal),
 * ni queda en el terreno de "revisar manualmente".
 */
export function esCorreccionSegura(columna: ColumnaConfigurada, correccion: CorreccionSugerida): boolean {
  if (columna.rol === 'paciente' || columna.codigoInterno === 'fechaEvento') return false
  if (correccion.texto.toLowerCase().includes('revisar manualmente')) return false
  return correccion.tipoSugerido !== null || correccion.marcarNoObligatorio
}

export interface MensajeFallo {
  mensaje: string
  /** Mensaje real del backend, para mostrar solo en un bloque colapsable. */
  detalleTecnico: string | null
}

// Pasos cuyo fallo puede filtrar vocabulario técnico del backend (mapeo,
// plantilla, columna de origen ya mapeada...). En estos pasos se sustituye
// el mensaje por uno genérico y se guarda el original como detalle técnico.
const PASOS_TECNICOS: ClavePaso[] = ['plantilla', 'mapeos', 'validar-columnas']

/**
 * Traduce el fallo del orquestador a un mensaje que un usuario no técnico
 * pueda entender. Para los pasos de preparación de la importación (donde el
 * backend puede mencionar "mapeo"/"plantilla"), sustituye el mensaje por uno
 * genérico y conserva el original como detalle técnico aparte.
 */
export function mensajeAmableFallo(pasoFallido: ClavePaso | null, error: string | null): MensajeFallo {
  if (pasoFallido && PASOS_TECNICOS.includes(pasoFallido)) {
    return {
      mensaje:
        'No se pudo preparar la importación porque hay columnas repetidas o inválidas. Revisa la fila de cabecera y las columnas detectadas.',
      detalleTecnico: error,
    }
  }
  return { mensaje: error ?? 'No se pudo completar la importación.', detalleTecnico: null }
}
