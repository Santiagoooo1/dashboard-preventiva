/**
 * Fase 6.9G.1 — clasificación de los campos clínicos filtrables del dashboard.
 *
 * En 6.9F la barra de filtros pintaba un selector por *cada* campo filtrable
 * del dataset (17 en DEMO_MVP_ILQ_part2), lo que saturaba la pantalla antes de
 * que el usuario pudiera analizar nada. Aquí se decide qué se ve de entrada
 * ("exploración rápida") y qué queda plegado ("exploración avanzada"), sin
 * perder ningún filtro: los avanzados siguen disponibles, solo dejan de
 * competir por la atención.
 */

export interface CampoFiltroCategoria {
  codigo: string
  /** Etiqueta ya lista para mostrar (preferimos un nombre corto al del dataset). */
  etiqueta: string
  tipoDato: string
  valores: string[]
}

export interface CamposClasificados {
  principales: CampoFiltroCategoria[]
  avanzados: CampoFiltroCategoria[]
}

/**
 * Campos que se muestran siempre, en este orden. Cada uno se busca por varios
 * códigos posibles porque el código real depende de cómo se llamara la columna
 * en el Excel del hospital; si ninguno existe en el dataset, ese filtro
 * sencillamente no se muestra (nunca un selector vacío).
 */
const CAMPOS_PRINCIPALES: { etiqueta: string; codigos: string[] }[] = [
  { etiqueta: 'Sexo', codigos: ['sexo', 'genero'] },
  { etiqueta: 'Procedimiento', codigos: ['procedimiento', 'intervencion', 'tipocirugia'] },
  { etiqueta: 'ASA', codigos: ['asa', 'asascore', 'clasificacionasa', 'riesgoasa'] },
  {
    etiqueta: 'Infección',
    codigos: ['infeccionlocalizacionquirurgica', 'infeccionquirurgica', 'infeccion', 'ilq'],
  },
]

const normalizar = (codigo: string) => codigo.toLowerCase().replace(/[^a-z0-9]/g, '')

/**
 * Códigos con los que un dataset puede estar identificando al *individuo*
 * (paciente, en contexto clínico). Se busca por lista porque el código real
 * depende de cómo se llamara la columna en el origen: no se puede asumir
 * siempre `pacienteCodigo`, y nada impide que un dataset futuro represente
 * individuos que no sean pacientes.
 */
const CODIGOS_INDIVIDUO = ['pacientecodigo', 'hc', 'nhc', 'historiaclinica', 'numerohistoria', 'idpaciente', 'paciente']

export interface CampoIndividuo {
  codigo: string
  /** Cómo llamarlo en la UI ("Paciente / HC" si se reconoce como paciente). */
  etiqueta: string
  valores: string[]
}

/**
 * Detecta, entre los campos del dataset, cuál identifica al individuo, en el
 * orden de preferencia de CODIGOS_INDIVIDUO. Devuelve null si ninguno encaja,
 * en cuyo caso el modo "Individuo" sencillamente no se ofrece.
 *
 * Solo devuelve código y etiqueta: los valores se cargan aparte (una llamada
 * al endpoint de valores únicos) para no acoplar la detección a la red.
 */
export function detectarCampoIndividuo(
  campos: { codigo: string; etiqueta: string; tipoDato: string }[],
): { codigo: string; etiqueta: string } | null {
  for (const candidato of CODIGOS_INDIVIDUO) {
    const campo = campos.find((c) => normalizar(c.codigo) === candidato)
    if (!campo) continue

    // "Paciente / HC" solo si el dataset lo identifica como tal; si el código
    // es otro, se respeta la etiqueta del dataset para no imponer vocabulario
    // clínico a datasets cuyos individuos no sean pacientes.
    const esPaciente = candidato.includes('paciente') || candidato === 'hc' || candidato === 'nhc'
    return { codigo: campo.codigo, etiqueta: esPaciente ? 'Paciente / HC' : campo.etiqueta }
  }

  return null
}

/**
 * Reparte los campos disponibles entre principales (en el orden fijo de
 * CAMPOS_PRINCIPALES) y avanzados (el resto, en el orden del dataset).
 */
export function clasificarCampos(campos: CampoFiltroCategoria[]): CamposClasificados {
  const principales: CampoFiltroCategoria[] = []
  const yaUsados = new Set<string>()

  for (const definicion of CAMPOS_PRINCIPALES) {
    const encontrado = campos.find(
      (c) => !yaUsados.has(c.codigo) && definicion.codigos.includes(normalizar(c.codigo)),
    )
    if (encontrado) {
      yaUsados.add(encontrado.codigo)
      principales.push({ ...encontrado, etiqueta: definicion.etiqueta })
    }
  }

  return {
    principales,
    avanzados: campos.filter((c) => !yaUsados.has(c.codigo)),
  }
}

/**
 * Valor legible para el usuario. Los campos BOOLEANO llegan del backend como
 * "true"/"false" (así los devuelve el endpoint de valores únicos); mostrarlos
 * tal cual sería exponer un tecnicismo en mitad de una pantalla clínica.
 */
export function etiquetaValor(campo: CampoFiltroCategoria, valor: string): string {
  if (campo.tipoDato !== 'BOOLEANO') return valor
  return formatearBooleano(valor) ?? valor
}

function formatearBooleano(valor: string): string | null {
  const v = valor.trim().toLowerCase()
  if (v === 'true' || v === '1') return 'Sí'
  if (v === 'false' || v === '0') return 'No'
  return null
}

/**
 * Etiqueta de categoría lista para mostrar en gráficas, tablas, leyendas y
 * tooltips. Se usa cuando solo se tiene la etiqueta que devolvió el backend y
 * no necesariamente el CampoClinico correspondiente.
 *
 * Con `campo` conocido y de tipo BOOLEANO se aplican todas las formas (true/1,
 * false/0). Sin `campo`, se convierten ÚNICAMENTE los literales "true"/"false":
 * "1" y "0" se dejan intactos a propósito, porque en este dominio son valores
 * clínicos legítimos (ASA 1, grados de contaminación…) y traducirlos a Sí/No
 * sería un error de lectura clínica, no una mejora tipográfica.
 */
export function formatearEtiquetaCategoria(
  etiqueta: string | null | undefined,
  campo?: CampoFiltroCategoria | null,
): string {
  if (etiqueta === null || etiqueta === undefined || etiqueta === '') return 'Sin dato'

  if (campo?.tipoDato === 'BOOLEANO') {
    return formatearBooleano(etiqueta) ?? etiqueta
  }

  const v = etiqueta.trim().toLowerCase()
  if (v === 'true') return 'Sí'
  if (v === 'false') return 'No'
  return etiqueta
}
