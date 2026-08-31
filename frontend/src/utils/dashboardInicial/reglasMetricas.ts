import type {
  BaseEvaluableDashboardDto,
  CampoMetricaMetadataDto,
  ConfiguracionMetricaDto,
  ConfiguracionWidgetDto,
  PropuestaWidgetDto,
  TipoMetrica,
  TipoResultado,
  TipoVisualizacion,
} from '../../api/types'
import { normalizarTexto } from '../importacionGuiada/sugerenciasColumnas'

/**
 * Tope del dashboard inicial. Sube de 12 a 14 en la Fase 6.9I.4 para dejar
 * sitio a "Pacientes únicos" y a la completitud sin desplazar las
 * distribuciones que ya se generaban. Sigue siendo un dashboard curado: NO se
 * crea un widget por columna.
 */
export const MAX_WIDGETS_DASHBOARD_INICIAL = 14

export interface PropuestaWidget {
  tipoVisualizacion: TipoVisualizacion
  ancho: number
  /**
   * Forma del resultado. Se aplica tras crear el widget, porque el endpoint de
   * alta solo acepta la visualización y el ancho. Ausente = ACTUAL.
   */
  tipoResultado?: TipoResultado
  configuracionWidget?: ConfiguracionWidgetDto
}

export interface PropuestaMetrica {
  codigo: string
  nombre: string
  descripcion?: string | null
  tipoMetrica: TipoMetrica
  configuracion: ConfiguracionMetricaDto
  unidad?: string | null
  decimales?: number | null
  widget: PropuestaWidget
}

// Campos que nunca deben convertirse en indicador, aunque coincidan con otra
// regla (identificadores personales y texto libre de apoyo, no clínico).
const PALABRAS_EXCLUIDAS = ['nombre', 'comentario', 'comentarios', 'observacion', 'observaciones']

// Orden de prioridad para elegir qué dos booleanos clínicos van en la fila 1
// como KPI; el resto (si caben en el tope) se añaden como KPI al final.
const PRIORIDAD_BOOLEANOS: string[][] = [
  ['infeccion', 'ilq'],
  ['reingreso'],
  ['exitus'],
  ['urgente'],
  ['cultivo'],
  ['profilaxis'],
]

const PALABRAS_CATEGORICAS = [
  'asa',
  'gravedad',
  'contaminacion',
  'abordaje',
  'localizacion',
  'motivo',
  'muestra',
  'microorganismo',
  'resistencia',
]

const CODIGOS_CAMPOS_CLAVE = new Set([
  'servicio',
  'sexo',
  'edad',
  'procedimiento',
  'diagnostico',
  'cie10',
  'pacienteCodigo',
  'fechaEvento',
])

function contieneAlguna(normal: string, palabras: string[]): boolean {
  return palabras.some((p) => new RegExp(`(^|\\W)${p}(\\W|$)`).test(normal))
}

function textoDelCampo(campo: CampoMetricaMetadataDto): string {
  return normalizarTexto(campo.etiqueta || campo.codigo)
}

function estaExcluido(campo: CampoMetricaMetadataDto): boolean {
  const codigoN = normalizarTexto(campo.codigo)
  if (codigoN === 'pacientecodigo' || codigoN === 'fechaevento') return true
  return contieneAlguna(textoDelCampo(campo), PALABRAS_EXCLUIDAS) || contieneAlguna(codigoN, PALABRAS_EXCLUIDAS)
}

/** snake_case sin acentos ni caracteres raros, a partir de un código o nombre libre. */
export function generarCodigoSnakeCase(texto: string): string {
  const limpio = texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .replace(/_+/g, '_')
  return limpio || 'campo'
}

function buscarCampo(campos: CampoMetricaMetadataDto[], codigo: string): CampoMetricaMetadataDto | undefined {
  return campos.find((c) => c.codigo === codigo)
}

function prioridadBooleano(campo: CampoMetricaMetadataDto): number {
  const n = textoDelCampo(campo)
  const codigoN = normalizarTexto(campo.codigo)
  const indice = PRIORIDAD_BOOLEANOS.findIndex((palabras) => contieneAlguna(n, palabras) || contieneAlguna(codigoN, palabras))
  return indice === -1 ? PRIORIDAD_BOOLEANOS.length : indice
}

function metricaPorcentaje(campo: CampoMetricaMetadataDto): PropuestaMetrica {
  return {
    codigo: `porcentaje_${generarCodigoSnakeCase(campo.codigo)}`,
    nombre: `% ${campo.etiqueta}`,
    tipoMetrica: 'PORCENTAJE',
    configuracion: {
      numerador: { filtros: [{ campo: campo.codigo, operador: 'EQ', valor: true }] },
      denominador: { filtros: [] },
    },
    widget: { tipoVisualizacion: 'KPI', ancho: 3 },
  }
}

function metricaDistribucion(
  campo: CampoMetricaMetadataDto,
  opciones?: { codigo?: string; nombre?: string; tipoVisualizacion?: TipoVisualizacion; ancho?: number },
): PropuestaMetrica {
  return {
    codigo: opciones?.codigo ?? `distribucion_${generarCodigoSnakeCase(campo.codigo)}`,
    nombre: opciones?.nombre ?? `Distribución por ${campo.etiqueta}`,
    tipoMetrica: 'DISTRIBUCION',
    configuracion: { campoAgrupacion: campo.codigo },
    widget: { tipoVisualizacion: opciones?.tipoVisualizacion ?? 'BARRAS', ancho: opciones?.ancho ?? 6 },
  }
}

/**
 * Convierte una propuesta del backend en la forma que usa el orquestador.
 *
 * Los widgets clínicos prioritarios se definen en el backend, junto al resto
 * del conocimiento de ILQ, y no se recalculan aquí: duplicar esas reglas en el
 * frontend acabaría con dos versiones que se separan a la primera corrección.
 */
function desdePropuestaBackend(propuesta: PropuestaWidgetDto): PropuestaMetrica {
  return {
    codigo: propuesta.codigoMetrica,
    nombre: propuesta.nombre,
    descripcion: propuesta.descripcion,
    tipoMetrica: propuesta.tipoMetrica,
    configuracion: propuesta.configuracion,
    unidad: propuesta.unidad,
    decimales: propuesta.decimales,
    widget: {
      tipoVisualizacion: propuesta.tipoVisualizacion,
      ancho: propuesta.ancho,
      tipoResultado: propuesta.tipoResultado,
      configuracionWidget: propuesta.configuracionWidget ?? undefined,
    },
  }
}

/**
 * Propone las métricas iniciales del dashboard a partir de la metadata real
 * del dataset (no del estado del asistente): funciona igual para datasets
 * importados desde /crear-dashboard o creados en modo avanzado.
 *
 * `bloqueClinico` son los indicadores prioritarios que el backend propone para
 * datasets de infección quirúrgica (Fase 6.9L). Van delante de todo y NO se
 * descuentan del tope: el médico pidió esos cinco, y que un widget genérico los
 * desplace es justamente el fallo que se está corrigiendo.
 */
export function proponerMetricas(
  campos: CampoMetricaMetadataDto[],
  bloqueClinico: PropuestaWidgetDto[] = [],
  baseEvaluable?: BaseEvaluableDashboardDto,
): PropuestaMetrica[] {
  // Población sobre la que cuentan los indicadores de ACTIVIDAD (volumen,
  // pacientes, completitud). Cuando el dataset tiene fecha de evento, una fila
  // sin fecha no es una intervención: es una ficha a medio rellenar, y contarla
  // convierte «46 intervenciones con el dato completo» en «55 registros al
  // 84 %». El resto de métricas no lo llevan — las clínicas ya definen su
  // propia base y las distribuciones describen categorías, no actividad.
  const filtrosActividad = baseEvaluable?.filtros ?? []
  const prioritarias = bloqueClinico.map(desdePropuestaBackend)

  // Campos que el bloque clínico ya explica: repetirlos como distribución
  // genérica daría dos gráficos del mismo campo, uno de ellos sin el filtro
  // clínico que lo hace legible.
  const camposYaCubiertos = new Set(
    bloqueClinico.map((p) => p.configuracion.campoAgrupacion).filter((c): c is string => Boolean(c)),
  )

  // Y campos que el bloque ya mide como porcentaje. Sin esto el dashboard
  // abriría con dos KPI sobre la infección: la tasa clínica (sobre las
  // intervenciones documentadas) y un «% Infección» genérico calculado sobre
  // todos los registros. Dos cifras distintas para lo mismo, y la segunda
  // siempre más baja porque cuenta como sanos los casos sin documentar.
  const camposYaMedidos = new Set(
    bloqueClinico
      .flatMap((p) => p.configuracion.numerador?.filtros ?? [])
      .map((f) => f.campo)
      .filter((c): c is string => Boolean(c)),
  )

  // La evolución mensual de ILQ ocupa el sitio de "Registros por mes": son el
  // mismo hueco del dashboard y la clínica es más informativa. La métrica
  // genérica no desaparece del sistema, solo de este dashboard concreto.
  const hayEvolucionClinica = prioritarias.some((p) => p.widget.tipoResultado === 'SERIE_TEMPORAL')
  const codigosPrioritarios = new Set(prioritarias.map((p) => p.codigo))

  const propuestas: PropuestaMetrica[] = []

  // 1. Volumen (siempre). El código se mantiene estable aunque cambie la
  // etiqueta: es el identificador de la métrica dentro del dataset.
  propuestas.push({
    codigo: 'total_registros',
    nombre: baseEvaluable?.etiquetaVolumen ?? 'Total de registros',
    tipoMetrica: 'CONTEO',
    configuracion: { filtros: filtrosActividad },
    widget: { tipoVisualizacion: 'KPI', ancho: 3 },
  })

  // 2. Pacientes únicos, si el dataset identifica al individuo.
  //
  // Sin esta métrica el dashboard cuenta registros y los llama pacientes, que
  // es el error de lectura más caro en vigilancia: un paciente reintervenido
  // aparece dos veces. Es posible desde que existe CONTEO_DISTINTO (6.9I.2).
  const campoPaciente = buscarCampo(campos, 'pacienteCodigo')
  if (campoPaciente) {
    propuestas.push({
      codigo: 'pacientes_unicos',
      nombre: 'Pacientes únicos',
      tipoMetrica: 'CONTEO_DISTINTO',
      // Un paciente que solo consta en una ficha vacía no es un paciente
      // intervenido.
      configuracion: { campoValor: 'pacienteCodigo', filtros: filtrosActividad },
      widget: { tipoVisualizacion: 'KPI', ancho: 3 },
    })
  }

  // 3. Edad media, si existe.
  const campoEdad = buscarCampo(campos, 'edad')
  if (campoEdad?.utilizableComoCampoValor) {
    propuestas.push({
      codigo: 'edad_media',
      nombre: 'Edad media',
      tipoMetrica: 'PROMEDIO',
      configuracion: { campoValor: 'edad', filtros: [] },
      widget: { tipoVisualizacion: 'KPI', ancho: 3 },
    })
  }

  // Booleanos clínicos, ordenados por prioridad clínica.
  const booleanos = campos
    .filter((c) => c.tipoDato === 'BOOLEANO' && !estaExcluido(c) && !camposYaMedidos.has(c.codigo))
    .map((campo) => ({ campo, prioridad: prioridadBooleano(campo) }))
    .sort((a, b) => a.prioridad - b.prioridad)
    .map(({ campo }) => metricaPorcentaje(campo))

  // Los dos primeros booleanos van en la fila 1, como KPI.
  propuestas.push(...booleanos.slice(0, 2))

  // Completitud del campo clínico más relevante (el primer booleano por
  // prioridad, que es el indicador principal del dataset).
  //
  // Un indicador sin saber sobre cuántos registros está informado no es
  // interpretable: un 3 % de infección puede significar que hay poca infección
  // o que casi nadie rellena la casilla, y son problemas opuestos.
  const campoParaCompletitud = campos
    .filter((c) => c.tipoDato === 'BOOLEANO' && !estaExcluido(c))
    .sort((a, b) => prioridadBooleano(a) - prioridadBooleano(b))[0]

  if (campoParaCompletitud) {
    propuestas.push({
      codigo: `completitud_${generarCodigoSnakeCase(campoParaCompletitud.codigo)}`,
      nombre: `Completitud de ${campoParaCompletitud.etiqueta}`,
      tipoMetrica: 'COMPLETITUD',
      // El denominador es la población evaluable, no el total de filas: si no,
      // las fichas sin datos parecen un problema de registro que no existe.
      configuracion: { campoValor: campoParaCompletitud.codigo, filtros: filtrosActividad },
      widget: { tipoVisualizacion: 'KPI', ancho: 3 },
    })
  }

  // Registros por mes: métrica CONTEO propia, distinta de total_registros,
  // porque el backend no permite la misma métrica en dos widgets del mismo
  // panel. Se omite cuando ya hay una evolución clínica, que responde a la
  // misma pregunta y además dice la tasa en vez del volumen.
  if (!hayEvolucionClinica) {
    propuestas.push({
      codigo: 'registros_por_mes',
      nombre: 'Registros por mes',
      tipoMetrica: 'CONTEO',
      configuracion: { filtros: [] },
      widget: {
        tipoVisualizacion: 'LINEAS',
        ancho: 12,
        tipoResultado: 'SERIE_TEMPORAL',
        configuracionWidget: { granularidad: 'MES', campoFecha: null, campoSegmentacion: null, campoAgrupacion: null },
      },
    })
  }

  // Servicio.
  const campoServicio = buscarCampo(campos, 'servicio')
  if (campoServicio?.utilizableComoCampoAgrupacion) {
    propuestas.push(metricaDistribucion(campoServicio, { codigo: 'registros_por_servicio', nombre: 'Registros por servicio' }))
  }

  // Sexo (DONUT en vez de BARRAS).
  const campoSexo = buscarCampo(campos, 'sexo')
  if (campoSexo?.utilizableComoCampoAgrupacion) {
    propuestas.push(
      metricaDistribucion(campoSexo, {
        codigo: 'distribucion_sexo',
        nombre: 'Distribución por sexo',
        tipoVisualizacion: 'DONUT',
      }),
    )
  }

  // Procedimiento.
  const campoProcedimiento = buscarCampo(campos, 'procedimiento')
  if (campoProcedimiento?.utilizableComoCampoAgrupacion) {
    propuestas.push(
      metricaDistribucion(campoProcedimiento, { codigo: 'distribucion_procedimiento', nombre: 'Distribución por procedimiento' }),
    )
  }

  // Diagnóstico/CIE-10 (el que exista de los dos).
  const campoDiagnostico = buscarCampo(campos, 'diagnostico') ?? buscarCampo(campos, 'cie10')
  if (campoDiagnostico?.utilizableComoCampoAgrupacion) {
    propuestas.push(
      metricaDistribucion(campoDiagnostico, { codigo: 'distribucion_diagnostico', nombre: 'Distribución por diagnóstico' }),
    )
  }

  // Resto: categóricas clínicamente relevantes y booleanos restantes,
  // en ese orden, hasta completar el tope.
  const categoricas = campos
    .filter(
      (c) =>
        c.tipoDato === 'TEXTO' &&
        !camposYaCubiertos.has(c.codigo) &&
        !CODIGOS_CAMPOS_CLAVE.has(c.codigo) &&
        !estaExcluido(c) &&
        c.utilizableComoCampoAgrupacion &&
        (contieneAlguna(textoDelCampo(c), PALABRAS_CATEGORICAS) || contieneAlguna(normalizarTexto(c.codigo), PALABRAS_CATEGORICAS)),
    )
    .map((campo) => metricaDistribucion(campo))

  const resto = [...categoricas, ...booleanos.slice(2)]
  for (const propuesta of resto) {
    if (propuestas.length >= MAX_WIDGETS_DASHBOARD_INICIAL) break
    propuestas.push(propuesta)
  }

  // El tope recorta lo genérico, nunca lo clínico: los prioritarios se añaden
  // después del recorte, delante de todo, y ninguno puede quedarse fuera.
  const genericas = propuestas
    .filter((p) => !codigosPrioritarios.has(p.codigo))
    .slice(0, MAX_WIDGETS_DASHBOARD_INICIAL)

  return [...prioritarias, ...genericas]
}
