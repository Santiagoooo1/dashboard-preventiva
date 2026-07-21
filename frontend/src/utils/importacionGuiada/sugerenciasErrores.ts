import type { ErrorFilaImportacionGenericaDto, TipoDato } from '../../api/types'
import type { ColumnaConfigurada } from './sugerenciasColumnas'

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

/** Agrupa la lista de errores de fila por columna, con muestra de valores. */
export function agruparErroresPorColumna(errores: ErrorFilaImportacionGenericaDto[]): GrupoErrorColumna[] {
  const mapa = new Map<
    string,
    { total: number; severidades: Set<string>; tipos: Map<string, number>; valores: Set<string> }
  >()

  for (const error of errores) {
    const columna = (error.nombreColumna ?? 'Columna desconocida').trim()
    if (!mapa.has(columna)) {
      mapa.set(columna, { total: 0, severidades: new Set(), tipos: new Map(), valores: new Set() })
    }
    const grupo = mapa.get(columna)!
    grupo.total += 1
    grupo.severidades.add(error.severidad)
    grupo.tipos.set(error.tipoError, (grupo.tipos.get(error.tipoError) ?? 0) + 1)
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
    }
  })
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
