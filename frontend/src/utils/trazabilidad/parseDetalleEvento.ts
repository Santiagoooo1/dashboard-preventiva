// El campo `detalle` de un evento de trazabilidad viaja como String desde el
// backend (normalmente JSON generado con Jackson, ver TrazabilidadImportacionTrabajoServiceImpl).
// Se trata como texto arbitrario de todos modos: si en algún caso no fuera un
// JSON válido, la pantalla no debe romperse.
export function parseDetalleEvento(detalle: string | null): unknown {
  if (!detalle || detalle.trim() === '') return null
  try {
    return JSON.parse(detalle)
  } catch {
    return detalle
  }
}

const ETIQUETAS_CONOCIDAS: Record<string, string> = {
  filasAfectadas: 'Filas afectadas',
  totalAfectadas: 'Total afectadas',
  estrategia: 'Estrategia',
  importacionGenericaId: 'Importación final',
  importacionTrabajoId: 'Copia interna',
  datasetId: 'Dataset',
  plantillaId: 'Plantilla',
  numeroFila: 'Fila',
  numeroFilaOriginal: 'Fila',
  nombreColumna: 'Columna',
  valorAnterior: 'Valor anterior',
  valorNuevo: 'Valor nuevo',
  tipoError: 'Tipo de problema',
  estadoResultante: 'Estado resultante',
  estadoAnterior: 'Estado anterior',
  totalErroresResultante: 'Total de errores',
  totalAdvertenciasResultante: 'Total de advertencias',
  totalFilasResultante: 'Total de filas',
  totalFilas: 'Total de filas',
  hashArchivoOriginal: 'Hash del archivo',
  nombreArchivoOriginal: 'Archivo',
  filasImportadas: 'Filas importadas',
  filasExcluidas: 'Filas excluidas',
}

function etiquetaClave(clave: string): string {
  if (ETIQUETAS_CONOCIDAS[clave]) return ETIQUETAS_CONOCIDAS[clave]
  const conEspacios = clave.replace(/([a-z0-9])([A-Z])/g, '$1 $2')
  const minuscula = conEspacios.toLowerCase()
  return minuscula.charAt(0).toUpperCase() + minuscula.slice(1)
}

// Vocabulario tipo enum del backend (p.ej. "FECHA", "NO_ADECUADA") se muestra
// en minúsculas para que no parezca una constante técnica.
function formatearValorSimple(valor: unknown): string {
  if (valor === null || valor === undefined) return '—'
  if (typeof valor === 'boolean') return valor ? 'Sí' : 'No'
  if (typeof valor === 'string') {
    if (/^[A-Z0-9_]+$/.test(valor) && valor.length > 1) {
      return valor.replace(/_/g, ' ').toLowerCase()
    }
    return valor
  }
  return String(valor)
}

const MAX_ITEMS_ARRAY = 10

function formatearObjetoCompacto(obj: Record<string, unknown>): string {
  return Object.entries(obj)
    .map(([clave, valor]) => `${etiquetaClave(clave)}: ${formatearValorCualquiera(valor)}`)
    .join('; ')
}

function formatearValorCualquiera(valor: unknown): string {
  if (Array.isArray(valor)) return formatearArray(valor)
  if (valor && typeof valor === 'object') return formatearObjetoCompacto(valor as Record<string, unknown>)
  return formatearValorSimple(valor)
}

// Trunca listas largas: no tiene sentido clínico mostrar cientos de filas
// afectadas, y evita reventar la pantalla con detalles muy grandes.
function formatearArray(valores: unknown[]): string {
  const items = valores.map(formatearValorCualquiera)
  if (items.length > MAX_ITEMS_ARRAY) {
    const resto = items.length - MAX_ITEMS_ARRAY
    return `${items.slice(0, MAX_ITEMS_ARRAY).join(', ')} y ${resto} más`
  }
  return items.join(', ')
}

// Uso puntual para un `detalle` cuya raíz es un array o un valor suelto (caso
// infrecuente: lo normal es que la raíz sea un objeto clave/valor).
export function formatearValorDetalle(valor: unknown): string {
  return formatearValorCualquiera(valor)
}

export interface ParDetalleEvento {
  clave: string
  etiqueta: string
  valor: string
}

export function formatearDetalleObjeto(detalle: Record<string, unknown>): ParDetalleEvento[] {
  return Object.entries(detalle).map(([clave, valor]) => ({
    clave,
    etiqueta: etiquetaClave(clave),
    valor: formatearValorCualquiera(valor),
  }))
}
