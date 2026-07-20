import type { ErrorFilaImportacionGenericaDto, TipoDato } from '../../api/types'
import type { ColumnaConfigurada } from './sugerenciasColumnas'

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
