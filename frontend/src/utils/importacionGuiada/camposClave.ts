import type { TipoDato } from '../../api/types'
import type { ColumnaConfigurada, RolClinico } from './sugerenciasColumnas'
import { CODIGO_CANONICO, desambiguarCodigos, normalizarTexto, sugerirCodigoInterno } from './sugerenciasColumnas'

// Roles que se asignan a una única columna a la vez ("fecha" se gestiona
// aparte, con su propia lógica de fecha principal / fechas secundarias).
export type RolClaveAsignable = Exclude<RolClinico, 'fecha' | 'categoria' | 'numero' | 'booleano' | 'texto' | 'ignorar'>

export interface CampoClaveDefinicion {
  rol: RolClinico
  etiqueta: string
  ayuda?: string
  /** Si es true, el select solo debe listar columnas con rol "fecha". */
  soloFechas?: boolean
  /** Si es true, no se ofrece la opción "(ninguna)": el campo es obligatorio de facto. */
  requerido?: boolean
}

export const CAMPOS_CLAVE: CampoClaveDefinicion[] = [
  { rol: 'paciente', etiqueta: 'Identificador del paciente', requerido: true },
  {
    rol: 'fecha',
    etiqueta: 'Fecha principal del dashboard',
    ayuda: 'Esta fecha se usará para filtros, evolución temporal y gráficos por mes.',
    soloFechas: true,
    requerido: true,
  },
  { rol: 'servicio', etiqueta: 'Servicio' },
  { rol: 'edad', etiqueta: 'Edad' },
  { rol: 'sexo', etiqueta: 'Sexo' },
  { rol: 'procedimiento', etiqueta: 'Procedimiento' },
  { rol: 'diagnostico', etiqueta: 'Diagnóstico/CIE-10' },
]

function esNombreCirugia(nombreOriginal: string): boolean {
  return /(^|\W)cirugia(\W|$)/.test(normalizarTexto(nombreOriginal))
}

/** Índice de la fecha recomendada como principal: la de cirugía si existe, si no la primera. */
export function sugerirFechaPrincipal(columnas: ColumnaConfigurada[]): number | null {
  const fechas = columnas.filter((c) => c.usar && c.rol === 'fecha')
  if (fechas.length === 0) return null
  const cirugia = fechas.find((c) => esNombreCirugia(c.nombreOriginal))
  return (cirugia ?? fechas[0]).indiceColumna
}

/**
 * Fija qué columna es la fecha principal (fechaEvento): la promueve a
 * esComun/obligatorio, y cualquier otra columna que tuviera antes ese código
 * recupera un código propio derivado de su nombre (deja de ser obligatoria).
 */
export function establecerFechaPrincipal(columnas: ColumnaConfigurada[], indiceColumna: number): ColumnaConfigurada[] {
  const actualizadas = columnas.map((c) => {
    if (c.indiceColumna === indiceColumna) {
      return { ...c, rol: 'fecha' as RolClinico, codigoInterno: 'fechaEvento', esComun: true, obligatorio: true }
    }
    if (c.codigoInterno === 'fechaEvento') {
      return { ...c, codigoInterno: sugerirCodigoInterno(c.nombreOriginal), esComun: false, obligatorio: false }
    }
    return c
  })
  return desambiguarCodigos(actualizadas)
}

/**
 * Fija qué columna ocupa un rol clave no-fecha (paciente/servicio/edad/sexo/
 * procedimiento/diagnóstico). Cualquier otra columna que ocupara ese rol pasa
 * a texto libre para no colisionar con la nueva.
 */
export function establecerRolClave(
  columnas: ColumnaConfigurada[],
  rol: RolClaveAsignable,
  indiceColumna: number,
): ColumnaConfigurada[] {
  const codigo = CODIGO_CANONICO[rol] ?? rol
  const actualizadas = columnas.map((c) => {
    if (c.indiceColumna === indiceColumna) {
      return { ...c, rol, codigoInterno: codigo, esComun: true, obligatorio: rol === 'paciente' }
    }
    if (c.rol === rol) {
      return { ...c, rol: 'texto' as RolClinico, codigoInterno: sugerirCodigoInterno(c.nombreOriginal), esComun: false, obligatorio: false }
    }
    return c
  })
  return desambiguarCodigos(actualizadas)
}

/** Quita la asignación de un rol clave opcional (opción "(ninguna)"): la columna que lo ocupaba pasa a texto libre. */
export function quitarRolClave(columnas: ColumnaConfigurada[], rol: RolClaveAsignable): ColumnaConfigurada[] {
  return columnas.map((c) => {
    if (c.rol !== rol) return c
    return { ...c, rol: 'texto' as RolClinico, codigoInterno: sugerirCodigoInterno(c.nombreOriginal), esComun: false, obligatorio: false }
  })
}

/**
 * Resuelve, a partir de las columnas recién detectadas (sin fecha principal
 * ni desambiguación de paciente todavía), los dos campos clave "singleton":
 * si hay varias columnas candidatas a paciente, solo la primera se queda como
 * tal; y la fecha principal se decide por nombre clínico (cirugía > primera).
 */
export function inicializarCamposClave(columnas: ColumnaConfigurada[]): ColumnaConfigurada[] {
  let resultado = columnas
  const candidatosPaciente = resultado.filter((c) => c.rol === 'paciente')
  if (candidatosPaciente.length > 1) {
    const sobrantes = new Set(candidatosPaciente.slice(1).map((c) => c.indiceColumna))
    resultado = resultado.map((c) =>
      sobrantes.has(c.indiceColumna)
        ? { ...c, rol: 'texto' as RolClinico, codigoInterno: sugerirCodigoInterno(c.nombreOriginal), esComun: false, obligatorio: false }
        : c,
    )
  }

  const indicePrincipal = sugerirFechaPrincipal(resultado)
  if (indicePrincipal !== null) {
    resultado = establecerFechaPrincipal(resultado, indicePrincipal)
  }

  return desambiguarCodigos(resultado)
}

export interface RevisionClinica {
  mensaje: string
  /** Si existe, "Aplicar sugerencia" cambia el tipo de dato de la columna a este valor. */
  tipoSugerido?: TipoDato
  /** Si existe, "Aplicar sugerencia" convierte esta columna en la fecha principal. */
  indiceFechaSugerida?: number
}

/**
 * Revisiones clínicas no bloqueantes: casos donde no hay ningún error técnico
 * pero conviene que un humano revise la decisión, basados solo en el nombre
 * de la columna (no hay acceso a los valores de las filas en este paso).
 */
export function detectarRevisionesClinicas(columnas: ColumnaConfigurada[]): Map<number, RevisionClinica> {
  const revisiones = new Map<number, RevisionClinica>()

  const principal = columnas.find((c) => c.usar && c.codigoInterno === 'fechaEvento')
  const cirugia = columnas.find((c) => c.usar && c.rol === 'fecha' && esNombreCirugia(c.nombreOriginal))
  if (principal && cirugia && principal.indiceColumna !== cirugia.indiceColumna) {
    revisiones.set(principal.indiceColumna, {
      mensaje: `La fecha principal es "${principal.nombreOriginal}", pero el archivo también tiene "${cirugia.nombreOriginal}". Revisa cuál debería ser la fecha principal.`,
      indiceFechaSugerida: cirugia.indiceColumna,
    })
  }

  for (const c of columnas) {
    if (!c.usar || revisiones.has(c.indiceColumna)) continue
    const n = normalizarTexto(c.nombreOriginal)

    if (c.tipoDato === 'BOOLEANO' && /(^|\W)localizacion(\W|$)/.test(n)) {
      revisiones.set(c.indiceColumna, {
        mensaje: 'Esta columna se ha marcado como Sí/No, pero parece indicar un lugar o categoría. Revisa si debería ser de tipo Texto.',
        tipoSugerido: 'TEXTO',
      })
      continue
    }

    if (c.tipoDato === 'TEXTO' && /(^|\W)urgente(\W|$)/.test(n)) {
      revisiones.set(c.indiceColumna, {
        mensaje: 'Esta columna parece indicar Sí/No. Revisa si debería ser de tipo Sí/No.',
        tipoSugerido: 'BOOLEANO',
      })
      continue
    }

    if (c.tipoDato === 'TEXTO' && /(^|\W)cultivo(\W|$)/.test(n) && /(^|\W)(ilq|infeccion)(\W|$)/.test(n)) {
      revisiones.set(c.indiceColumna, {
        mensaje: 'Esta columna parece indicar Sí/No. Revisa si debería ser de tipo Sí/No.',
        tipoSugerido: 'BOOLEANO',
      })
    }
  }

  return revisiones
}

// Nombres de cabecera que en realidad parecen un valor de celda (probable
// desajuste de la fila de cabecera: el archivo se leyó a partir de una fila de
// datos en vez de la fila con los nombres de columna). "si"/"no" cubren
// también "SÍ" y "NO", ya que normalizarTexto() quita los acentos.
const NOMBRES_SOSPECHOSOS = ['true', 'false', 'verdadero', 'falso', 'si', 'no', '0', '1']

/**
 * Detecta columnas cuyo nombre parece un valor (TRUE/FALSE/SÍ/NO/0/1/vacío) en
 * vez de un nombre de columna real, típico de haber elegido mal la fila de
 * cabecera al analizar el archivo.
 */
export function detectarColumnasSospechosas(columnas: ColumnaConfigurada[]): Map<number, string> {
  const sospechosas = new Map<number, string>()
  for (const c of columnas) {
    const n = normalizarTexto(c.nombreOriginal)
    if (n === '' || NOMBRES_SOSPECHOSOS.includes(n)) {
      sospechosas.set(c.indiceColumna, 'Esta columna parece un valor, no un nombre de columna.')
    }
  }
  return sospechosas
}

export interface GrupoDuplicado {
  veces: number
  columnasAfectadas: string[]
}

/**
 * Detecta columnas duplicadas (nombre exacto o normalizado) entre las
 * columnas usadas. Ignora `usar=false`: una columna ya descartada no cuenta
 * como duplicado activo.
 */
export function detectarColumnasDuplicadas(columnas: ColumnaConfigurada[]): Map<number, GrupoDuplicado> {
  const grupos = new Map<string, ColumnaConfigurada[]>()
  for (const c of columnas) {
    if (!c.usar) continue
    const clave = normalizarTexto(c.nombreOriginal)
    if (!grupos.has(clave)) grupos.set(clave, [])
    grupos.get(clave)!.push(c)
  }

  const resultado = new Map<number, GrupoDuplicado>()
  for (const miembros of grupos.values()) {
    if (miembros.length < 2) continue
    const columnasAfectadas = miembros.map((m) => m.nombreOriginal)
    for (const miembro of miembros) {
      resultado.set(miembro.indiceColumna, { veces: miembros.length, columnasAfectadas })
    }
  }
  return resultado
}

/**
 * Deja solo la primera aparición de cada nombre (exacto o normalizado) como
 * `usar=true`; el resto de apariciones duplicadas pasan a `usar=false`. Las
 * columnas sin duplicar no se tocan.
 */
export function desmarcarDuplicadasAutomaticamente(columnas: ColumnaConfigurada[]): ColumnaConfigurada[] {
  const vistos = new Set<string>()
  return columnas.map((c) => {
    if (!c.usar) return c
    const clave = normalizarTexto(c.nombreOriginal)
    if (vistos.has(clave)) return { ...c, usar: false }
    vistos.add(clave)
    return c
  })
}

/**
 * Última validación antes de invocar al orquestador (crear dataset, campos,
 * plantilla, mapeos, validar e importar). Se ejecuta tanto en el primer
 * intento como en cada revalidación, para que ninguna de las dos rutas pueda
 * saltarse las mismas comprobaciones.
 */
export function validarColumnasParaCrear(columnas: ColumnaConfigurada[]): string | null {
  const usadas = columnas.filter((c) => c.usar && c.rol !== 'ignorar')
  if (usadas.length === 0) {
    return 'Selecciona al menos una columna para continuar.'
  }
  const sospechosas = detectarColumnasSospechosas(columnas)
  if (columnas.some((c) => c.usar && sospechosas.has(c.indiceColumna))) {
    return 'Hay columnas sospechosas seleccionadas. Ignóralas o cambia la fila de cabecera antes de continuar.'
  }
  const duplicadas = detectarColumnasDuplicadas(columnas)
  if (columnas.some((c) => c.usar && duplicadas.has(c.indiceColumna))) {
    return 'Hay columnas repetidas en el archivo. Cambia la fila de cabecera o desmarca columnas duplicadas antes de continuar.'
  }
  if (!usadas.some((c) => c.codigoInterno === 'pacienteCodigo')) {
    return 'Falta el identificador del paciente. Selecciónalo en «Campos clave del dashboard».'
  }
  if (!usadas.some((c) => c.codigoInterno === 'fechaEvento')) {
    return 'Falta la fecha principal del dashboard. Selecciónala en «Campos clave del dashboard».'
  }
  return null
}
