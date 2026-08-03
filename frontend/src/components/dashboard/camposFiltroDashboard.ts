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
  if (valor === 'true') return 'Sí'
  if (valor === 'false') return 'No'
  return valor
}
