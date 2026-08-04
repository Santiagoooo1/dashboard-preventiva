import type { DashboardWidgetDto, FiltroMetricaDto } from '../../api/types'
import type { CampoFiltroCategoria } from './camposFiltroDashboard'
import type { ModoExploracion } from './capacidadesExploracionWidget'

/**
 * Fase 6.9H.1 — selección categórica desde una gráfica (cross-filtering).
 *
 * Nivel 3 de la arquitectura analítica: nace de pulsar un dato dentro de una
 * visualización y recalcula el RESTO del dashboard, conservando intacto el
 * widget de origen para no perder el contexto desde el que se navegó.
 */

export interface SeleccionCategorica {
  tipo: 'CATEGORIA'
  widgetOrigenId: number
  /** Código técnico del campo clínico. Nunca deducido del título ni del eje. */
  campo: string
  operador: 'EQ'
  /** Valor tal como lo devolvió el backend ("true", "COLECISTECTOMIA"). */
  valorOriginal: string
  /** Cómo se muestra al usuario ("Sí", "COLECISTECTOMIA"). */
  etiquetaVisible: string
  /** Nombre legible del campo ("Infección", "Procedimiento"). */
  etiquetaCampo: string
}

/**
 * Selección de un PERIODO. Una etiqueta temporal ("2026-01") no es un valor
 * categórico: se traduce a un rango real de fechas, que es lo único que el
 * backend sabe filtrar.
 */
export interface SeleccionTemporal {
  tipo: 'TEMPORAL'
  widgetOrigenId: number
  /** Campo de tipo FECHA sobre el que se aplica el rango. */
  campoFecha: string
  granularidad: string
  /** Etiqueta técnica del backend ("2026-01"). Nunca se traduce ni se envía. */
  periodoOriginal: string
  /** Límites YYYY-MM-DD, ambos INCLUSIVOS (verificado contra el backend). */
  fechaDesde: string
  fechaHasta: string
  /** Texto para el usuario ("enero de 2026"). */
  etiquetaVisible: string
  etiquetaCampo: string
}

export type SeleccionGrafica = SeleccionCategorica | SeleccionTemporal

export interface CapacidadSeleccionWidget {
  seleccionable: boolean
  campo: string | null
  etiquetaCampo: string | null
  motivoNoSeleccionable?: string
}

/**
 * De dónde sale el campo técnico de cada widget, por orden de fiabilidad. El
 * campo SIEMPRE procede de configuración o metadata real: nunca del título del
 * widget, del nombre de la métrica ni del texto (posiblemente truncado) del eje.
 *
 * @param campoAgrupacionMetrica `configuracion.campoAgrupacion` de la métrica,
 *        única fuente para las DISTRIBUCION (su agrupación vive en la métrica,
 *        no en el resultado).
 */
export function resolverSeleccionWidget(
  widget: DashboardWidgetDto,
  modo: ModoExploracion,
  campoAgrupacionLocal: string,
  campoAgrupacionMetrica: string | null,
  camposConocidos: CampoFiltroCategoria[],
): CapacidadSeleccionWidget {
  const no = (motivo: string): CapacidadSeleccionWidget => ({
    seleccionable: false,
    campo: null,
    etiquetaCampo: null,
    motivoNoSeleccionable: motivo,
  })

  if (widget.estado === 'ERROR') return no('Este indicador no se ha podido calcular.')

  // El widget ya está acotado a un individuo: usarlo como origen global
  // mezclaría el ámbito local con el del dashboard.
  if (modo === 'INDIVIDUO') return no('La vista individual no genera filtros para el resto del dashboard.')

  // Las series temporales se seleccionan por periodo, no por categoría:
  // lo resuelve `resolverSeleccionTemporalWidget`.
  if (widget.tipoResultado === 'SERIE_TEMPORAL') return no('Esta serie se selecciona por periodo.')

  let campo: string | null = null
  if (modo === 'CATEGORIA' && campoAgrupacionLocal) {
    campo = campoAgrupacionLocal
  } else if (widget.tipoResultado === 'COMPARATIVA' && widget.comparativa?.agrupadoPor) {
    campo = widget.comparativa.agrupadoPor
  } else if (widget.resultadoActual?.items && campoAgrupacionMetrica) {
    campo = campoAgrupacionMetrica
  }

  if (!campo) return no('Este indicador no está desglosado por ninguna categoría.')

  const conocido = camposConocidos.find((c) => c.codigo === campo)
  return { seleccionable: true, campo, etiquetaCampo: conocido?.etiqueta ?? campo }
}

/** Dos selecciones categóricas son la misma si coinciden campo y valor técnico. */
export function esMismaSeleccion(
  seleccion: SeleccionGrafica | null,
  campo: string,
  valorOriginal: string,
): boolean {
  return (
    seleccion !== null &&
    seleccion.tipo === 'CATEGORIA' &&
    seleccion.campo === campo &&
    seleccion.valorOriginal === valorOriginal
  )
}

/** Dos selecciones temporales son la misma si coinciden campo de fecha y periodo. */
export function esMismaSeleccionTemporal(
  seleccion: SeleccionGrafica | null,
  campoFecha: string,
  periodoOriginal: string,
): boolean {
  return (
    seleccion !== null &&
    seleccion.tipo === 'TEMPORAL' &&
    seleccion.campoFecha === campoFecha &&
    seleccion.periodoOriginal === periodoOriginal
  )
}

/**
 * Granularidades que el backend sabe calcular (enum `Granularidad`). No hay
 * DIA ni SEMANA: si aparecieran, habría que ampliar el enum del backend y
 * decidir su convención de semana antes de permitir la selección.
 */
export const GRANULARIDADES_SOPORTADAS = ['MES', 'TRIMESTRE', 'ANIO'] as const

const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]
const ORDINALES_TRIMESTRE = ['primer', 'segundo', 'tercer', 'cuarto']

export interface RangoTemporal {
  fechaDesde: string
  fechaHasta: string
  etiquetaVisible: string
}

/** Formato YYYY-MM-DD estricto: descarta cualquier cosa que no sea una fecha plana. */
const ES_FECHA_PLANA = /^\d{4}-\d{2}-\d{2}$/

/**
 * Convierte un periodo en un rango de fechas real.
 *
 * DECISIÓN CLAVE: el rango NO se calcula en el frontend, se toma de
 * `fechaInicio`/`fechaFin`, que el backend YA envía en cada `PuntoSerieDto`
 * (los produce `PeriodoTemporalUtil.calcularPeriodo`). Derivarlo aquí obligaría
 * a reimplementar fin de mes, años bisiestos y límites de trimestre, y a
 * mantener esa copia sincronizada con el backend. Aquí solo se valida el
 * formato y se traduce la etiqueta.
 *
 * Las fechas llegan como YYYY-MM-DD planas (verificado): no se construye
 * ningún `Date`, así que no hay desplazamiento por zona horaria.
 *
 * Devuelve null si el periodo no es interpretable con seguridad; en ese caso
 * el punto no debe ser seleccionable.
 */
export function resolverRangoTemporal(
  periodo: string,
  granularidad: string,
  fechaInicio: string | null | undefined,
  fechaFin: string | null | undefined,
): RangoTemporal | null {
  if (!periodo || !granularidad) return null
  if (!GRANULARIDADES_SOPORTADAS.includes(granularidad as (typeof GRANULARIDADES_SOPORTADAS)[number])) return null
  if (!fechaInicio || !fechaFin) return null
  if (!ES_FECHA_PLANA.test(fechaInicio) || !ES_FECHA_PLANA.test(fechaFin)) return null
  if (fechaInicio > fechaFin) return null

  const etiquetaVisible = etiquetaDePeriodo(periodo, granularidad)
  if (!etiquetaVisible) return null

  return { fechaDesde: fechaInicio, fechaHasta: fechaFin, etiquetaVisible }
}

/**
 * Etiqueta legible en español. Solo acepta los formatos que el backend produce
 * de verdad: "2026-01" (MES), "2026-Q1" (TRIMESTRE), "2026" (ANIO). Cualquier
 * otro devuelve null y el periodo deja de ser seleccionable, en vez de
 * arriesgar una interpretación equivocada.
 */
export function etiquetaDePeriodo(periodo: string, granularidad: string): string | null {
  if (granularidad === 'MES') {
    const m = /^(\d{4})-(\d{2})$/.exec(periodo)
    if (!m) return null
    const mes = Number(m[2])
    if (mes < 1 || mes > 12) return null
    return `${MESES[mes - 1]} de ${m[1]}`
  }

  if (granularidad === 'TRIMESTRE') {
    const m = /^(\d{4})-Q([1-4])$/.exec(periodo)
    if (!m) return null
    return `${ORDINALES_TRIMESTRE[Number(m[2]) - 1]} trimestre de ${m[1]}`
  }

  if (granularidad === 'ANIO') {
    const m = /^(\d{4})$/.exec(periodo)
    if (!m) return null
    return `año ${m[1]}`
  }

  return null
}

/**
 * Filtros que viajan al backend para un periodo. Se usan GTE y LTE — los dos
 * únicos operadores de rango del enum `OperadorFiltro` (no existe BETWEEN) — y
 * ambos son INCLUSIVOS, comprobado con GTE=LTE=2026-01-07, que devuelve 2
 * registros.
 *
 * Nunca se envía `EQ` con "2026-01": el backend compararía una fecha completa
 * contra un texto de periodo y no encontraría nada.
 */
export function filtrosDeSeleccionTemporal(seleccion: SeleccionTemporal): FiltroMetricaDto[] {
  return [
    { campo: seleccion.campoFecha, operador: 'GTE', valor: seleccion.fechaDesde },
    { campo: seleccion.campoFecha, operador: 'LTE', valor: seleccion.fechaHasta },
  ]
}

/**
 * Filtro que viaja al backend. Usa SIEMPRE el valor técnico: enviar "Sí" en
 * lugar de "true" no encontraría ningún registro.
 */
export function filtroDeSeleccion(seleccion: SeleccionCategorica): FiltroMetricaDto {
  return { campo: seleccion.campo, operador: seleccion.operador, valor: seleccion.valorOriginal }
}

/** Filtros globales AND selección gráfica, en el orden que espera el endpoint. */
export function combinarFiltros(
  filtrosGlobales: FiltroMetricaDto[],
  seleccion: SeleccionGrafica | null,
): FiltroMetricaDto[] {
  if (!seleccion) return [...filtrosGlobales]
  return seleccion.tipo === 'TEMPORAL'
    ? [...filtrosGlobales, ...filtrosDeSeleccionTemporal(seleccion)]
    : [...filtrosGlobales, filtroDeSeleccion(seleccion)]
}

/**
 * Alterna: pulsar lo ya seleccionado lo quita, cualquier otra cosa lo
 * sustituye. Solo hay una selección activa, así que una temporal reemplaza a
 * una categórica y viceversa.
 */
export function alternarSeleccion(
  actual: SeleccionGrafica | null,
  nueva: SeleccionGrafica,
): SeleccionGrafica | null {
  const esLaMisma =
    nueva.tipo === 'TEMPORAL'
      ? esMismaSeleccionTemporal(actual, nueva.campoFecha, nueva.periodoOriginal)
      : esMismaSeleccion(actual, nueva.campo, nueva.valorOriginal)

  return esLaMisma ? null : nueva
}

/**
 * Filtros del SUBCONJUNTO activo (Fase 6.9H.3): exactamente los mismos que
 * recibe el dashboard cruzado. Es un alias intencionado de `combinarFiltros`
 * para que exista un único punto de construcción: si el detalle y los gráficos
 * pudieran divergir, la tabla mostraría una población distinta de la que
 * resumen los KPI.
 */
export function filtrosDelSubconjunto(
  filtrosGlobales: FiltroMetricaDto[],
  seleccion: SeleccionGrafica | null,
): FiltroMetricaDto[] {
  return combinarFiltros(filtrosGlobales, seleccion)
}

/**
 * Firma determinista de un conjunto de filtros: identifica el subconjunto para
 * la caché y para descartar respuestas obsoletas. INCLUYE los valores, porque
 * sin ellos `sexo=HOMBRE` y `sexo=MUJER` tendrían la misma firma y una
 * respuesta vieja podría darse por válida.
 *
 * Por eso NO debe registrarse en logs ni incluirse en mensajes de error: puede
 * contener valores clínicos. Para trazar, usar `firmaFiltrosAnonima`.
 */
export function firmaFiltros(filtros: FiltroMetricaDto[]): string {
  return filtros
    .map((f) => `${f.campo} ${f.operador} ${JSON.stringify(f.valor ?? null)}`)
    .sort()
    .join('|')
}

/** Firma sin valores clínicos: describe la FORMA del filtro, apta para trazas. */
export function firmaFiltrosAnonima(filtros: FiltroMetricaDto[]): string {
  return filtros
    .map((f) => `${f.campo}:${f.operador}`)
    .sort()
    .join('|')
}

export interface CapacidadSeleccionTemporal {
  seleccionable: boolean
  campoFecha: string | null
  granularidad: string | null
  motivoNoSeleccionable?: string
}

/**
 * Capacidad de selección TEMPORAL de un widget.
 *
 * `campoFecha` procede de la configuración persistida del widget; cuando es
 * null, el backend usa su constante `CAMPO_FECHA_DEFECTO` ("fechaEvento"), y
 * ese valor por defecto se valida contra `camposFechaPermitidos` del panel para
 * no depender de una suposición. La granularidad procede de la respuesta
 * (`serieTemporal.granularidad`), no del texto del periodo.
 */
export function resolverSeleccionTemporalWidget(
  widget: DashboardWidgetDto,
  modo: ModoExploracion,
  campoFechaConfigurado: string | null,
  camposFechaPermitidos: string[],
): CapacidadSeleccionTemporal {
  const no = (motivo: string): CapacidadSeleccionTemporal => ({
    seleccionable: false,
    campoFecha: null,
    granularidad: null,
    motivoNoSeleccionable: motivo,
  })

  if (widget.estado === 'ERROR') return no('Este indicador no se ha podido calcular.')
  if (modo === 'INDIVIDUO') return no('La vista individual no genera filtros para el resto del dashboard.')
  if (widget.tipoResultado !== 'SERIE_TEMPORAL' || !widget.serieTemporal) return no('Este indicador no es una serie temporal.')

  const granularidad = widget.serieTemporal.granularidad
  if (!granularidad || !GRANULARIDADES_SOPORTADAS.includes(granularidad as (typeof GRANULARIDADES_SOPORTADAS)[number])) {
    return no('La granularidad de esta serie no permite seleccionar periodos.')
  }

  const campoFecha = campoFechaConfigurado ?? CAMPO_FECHA_POR_DEFECTO
  if (!camposFechaPermitidos.includes(campoFecha)) {
    return no('No se ha podido identificar el campo de fecha de esta serie.')
  }

  return { seleccionable: true, campoFecha, granularidad }
}

/**
 * Valor de `MetricaAnaliticaServiceImpl.CAMPO_FECHA_DEFECTO`: el campo que el
 * backend usa cuando la configuración del widget no especifica ninguno. No es
 * una inferencia del título — es el contrato del endpoint —, y aun así se
 * comprueba contra los campos de fecha reales del dataset antes de usarlo.
 */
export const CAMPO_FECHA_POR_DEFECTO = 'fechaEvento'

export interface IntegridadCruce {
  completa: boolean
  /** Widgets del dashboard base que la respuesta cruzada no trae. */
  faltantes: number[]
  /** Widgets que llegan y no estaban en el base: se ignoran al pintar. */
  inesperados: number[]
}

/**
 * Comprueba que la respuesta cruzada cubre TODOS los widgets del dashboard
 * base. Es la condición para aplicarla: si faltara alguno y se rellenara con su
 * resultado base, el dashboard mostraría a la vez indicadores calculados sobre
 * dos poblaciones distintas (unos con la selección aplicada y otros sin ella).
 * Eso es peor que no actualizar nada, porque las cifras dejan de ser
 * comparables entre tarjetas sin que el usuario pueda notarlo.
 *
 * Función pura para poder verificarla sin montar el dashboard.
 */
export function validarIntegridadCruce(
  idsEsperados: number[],
  widgetsRecibidos: { panelMetricaId: number }[],
): IntegridadCruce {
  const recibidos = new Set(widgetsRecibidos.map((w) => w.panelMetricaId))
  const esperados = new Set(idsEsperados)

  const faltantes = idsEsperados.filter((id) => !recibidos.has(id))
  const inesperados = widgetsRecibidos.map((w) => w.panelMetricaId).filter((id) => !esperados.has(id))

  return { completa: faltantes.length === 0, faltantes, inesperados }
}

/** Texto accesible de un elemento seleccionable. */
export function etiquetaAccesibleSeleccion(
  etiquetaCampo: string,
  etiquetaVisible: string,
  valorNumerico: string,
  activa: boolean,
): string {
  return activa
    ? `${etiquetaCampo}: ${etiquetaVisible} seleccionado. Pulsar para quitar.`
    : `Seleccionar ${etiquetaCampo}: ${etiquetaVisible}. Total: ${valorNumerico}.`
}
