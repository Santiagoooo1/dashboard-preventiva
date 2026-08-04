import type { DashboardWidgetDto, FiltroMetricaDto, Granularidad } from '../../api/types'
import { comparativaMetrica, ejecutarMetrica, serieTemporalMetrica } from '../../api/metricasApi'
import type { ModoExploracion } from './capacidadesExploracionWidget'
import type { CampoFiltroCategoria, CampoIndividuo } from './camposFiltroDashboard'

/**
 * Fase 6.9G.2 — ejecución de la exploración local de UN widget.
 *
 * No hay endpoint nuevo: se reutilizan los tres que ya existen y que desde
 * 6.9F aceptan una lista de filtros (`/ejecutar`, `/comparativa`,
 * `/serie-temporal`). El motor de cálculo (conteos, porcentajes, promedios,
 * agrupaciones) sigue viviendo exclusivamente en el backend; aquí solo se
 * elige a cuál llamar y se envuelve la respuesta en el mismo
 * `DashboardWidgetDto` que ya sabe pintar `DashboardWidgetRenderer`.
 *
 * Nada de esto se persiste: son ejecuciones de lectura.
 */

export interface PeticionExploracion {
  /** Widget tal como lo devolvió el dashboard (configuración persistida). */
  widget: DashboardWidgetDto
  modo: ModoExploracion
  campoAgrupacion: string
  campoIndividuo: string | null
  valorIndividuo: string
  /** Filtros globales del dashboard, ya aplicados. Se combinan con el local en AND. */
  filtrosGlobales: FiltroMetricaDto[]
  fechaDesde: string | null
  fechaHasta: string | null
  campoFecha: string | null
  campoSegmentacion: string | null
}

/**
 * Visualización adecuada para la vista local. Solo afecta al render temporal:
 * el `tipoVisualizacion` persistido del widget no se toca. Evita resultados
 * absurdos como un valor único pintado en barras, o una comparativa de nueve
 * categorías intentando renderizarse como indicador.
 */
function visualizacionParaVistaLocal(actual: string, modo: ModoExploracion, esValorUnico: boolean): string {
  if (modo === 'CATEGORIA') {
    return actual === 'KPI' || actual === 'TARJETA' ? 'BARRAS' : actual
  }
  if (esValorUnico && (actual === 'BARRAS' || actual === 'DONUT' || actual === 'PIE' || actual === 'LINEAS')) {
    return 'KPI'
  }
  return actual
}

export async function ejecutarExploracionLocal(
  peticion: PeticionExploracion,
  signal?: AbortSignal,
): Promise<DashboardWidgetDto> {
  const { widget, modo, filtrosGlobales, fechaDesde, fechaHasta } = peticion

  // Global AND local: el backend evalúa la lista entera con cumpleTodos().
  const filtros: FiltroMetricaDto[] = [...filtrosGlobales]
  if (modo === 'INDIVIDUO' && peticion.campoIndividuo && peticion.valorIndividuo) {
    filtros.push({ campo: peticion.campoIndividuo, operador: 'EQ', valor: peticion.valorIndividuo })
  }

  const base = { fechaDesde, fechaHasta, filtrosGlobales: filtros }

  if (modo === 'CATEGORIA') {
    const comparativa = await comparativaMetrica(
      widget.metricaId,
      { ...base, campoAgrupacion: peticion.campoAgrupacion },
      signal,
    )
    return {
      ...widget,
      tipoVisualizacion: visualizacionParaVistaLocal(widget.tipoVisualizacion, modo, false),
      tipoResultado: 'COMPARATIVA',
      comparativa,
      resultadoActual: null,
      serieTemporal: null,
      estado: 'OK',
      error: null,
    }
  }

  // Modo INDIVIDUO: se conserva la forma de resultado del widget, solo se
  // acota el conjunto de registros al individuo elegido.
  if (widget.tipoResultado === 'SERIE_TEMPORAL' && widget.serieTemporal?.granularidad) {
    const serieTemporal = await serieTemporalMetrica(
      widget.metricaId,
      {
        ...base,
        granularidad: widget.serieTemporal.granularidad as Granularidad,
        campoFecha: peticion.campoFecha,
        campoSegmentacion: peticion.campoSegmentacion,
      },
      signal,
    )
    return { ...widget, tipoResultado: 'SERIE_TEMPORAL', serieTemporal, resultadoActual: null, comparativa: null, estado: 'OK', error: null }
  }

  if (widget.tipoResultado === 'COMPARATIVA' && widget.comparativa?.agrupadoPor) {
    const comparativa = await comparativaMetrica(
      widget.metricaId,
      { ...base, campoAgrupacion: widget.comparativa.agrupadoPor },
      signal,
    )
    return { ...widget, tipoResultado: 'COMPARATIVA', comparativa, resultadoActual: null, serieTemporal: null, estado: 'OK', error: null }
  }

  const resultadoActual = await ejecutarMetrica(widget.metricaId, base, signal)
  const esValorUnico = resultadoActual.valor !== null && !resultadoActual.items
  return {
    ...widget,
    tipoVisualizacion: visualizacionParaVistaLocal(widget.tipoVisualizacion, modo, esValorUnico),
    tipoResultado: 'ACTUAL',
    resultadoActual,
    serieTemporal: null,
    comparativa: null,
    estado: 'OK',
    error: null,
  }
}

/** Nombre corto de lo que hay bajo exploración: "Sexo", "Paciente H003". Null si no hay. */
export function etiquetaExploracionActiva(
  modo: ModoExploracion,
  campoAgrupacion: string,
  valorIndividuo: string,
  camposAgrupables: CampoFiltroCategoria[],
  campoIndividuo: CampoIndividuo | null,
): string | null {
  if (modo === 'CATEGORIA' && campoAgrupacion) {
    return camposAgrupables.find((c) => c.codigo === campoAgrupacion)?.etiqueta ?? campoAgrupacion
  }
  if (modo === 'INDIVIDUO' && valorIndividuo && campoIndividuo) {
    const nombre = campoIndividuo.etiqueta === 'Paciente / HC' ? 'Paciente' : 'Individuo'
    return `${nombre} ${valorIndividuo}`
  }
  return null
}

/**
 * Subtítulo del widget: UNA sola frase, nunca la concatenación de la
 * descripción del tipo de resultado y la de la exploración local. Antes se
 * encadenaban y salía "Agrupado por categoría · agrupado por Sexo", que dice
 * dos veces lo mismo con distintas palabras.
 *
 * Durante una exploración manda la exploración; en modo Actual manda el tipo
 * de resultado persistido. Un modo elegido pero incompleto no describe nada
 * (no afirma una agrupación que aún no existe).
 */
export function resolverSubtituloWidget(
  modo: ModoExploracion,
  tipoResultado: string,
  etiquetaExploracion: string | null,
  etiquetasTipoResultado: Record<string, string>,
): string {
  if (modo === 'CATEGORIA') {
    return etiquetaExploracion ? `Desglosado por ${etiquetaExploracion}` : 'Sin desglose seleccionado'
  }
  if (modo === 'INDIVIDUO') {
    return etiquetaExploracion ? `Vista individual · ${etiquetaExploracion}` : 'Sin individuo seleccionado'
  }
  return etiquetasTipoResultado[tipoResultado] ?? tipoResultado
}

/**
 * El widget no tiene NADA que representar: ni categorías, ni puntos, ni valor.
 *
 * Un valor 0 NO entra aquí: "cero casos" es un resultado clínico legítimo y
 * distinto de "no hay datos". Para el caso de estructura presente pero todos
 * los valores a cero existe `widgetTodoCero`, que se comunica aparte.
 */
export function widgetSinDatos(widget: DashboardWidgetDto): boolean {
  if (widget.estado !== 'OK') return false

  if (widget.comparativa) {
    return widget.comparativa.items.length === 0
  }

  if (widget.serieTemporal) {
    const s = widget.serieTemporal
    if (s.series) return s.series.length === 0
    if (s.puntos) return s.puntos.length === 0
    return true
  }

  if (widget.resultadoActual) {
    const r = widget.resultadoActual
    if (r.items) return r.items.length === 0
    return r.valor === null || r.valor === undefined
  }

  return false
}

/** Hay estructura (categorías, puntos o un valor) pero todo suma cero. */
export function widgetTodoCero(widget: DashboardWidgetDto): boolean {
  if (widget.estado !== 'OK' || widgetSinDatos(widget)) return false

  if (widget.comparativa) {
    return widget.comparativa.items.every((i) => !i.valor)
  }

  if (widget.serieTemporal) {
    const s = widget.serieTemporal
    if (s.series) return s.series.every((serie) => serie.puntos.every((p) => !p.valor))
    if (s.puntos) return s.puntos.every((p) => !p.valor)
  }

  if (widget.resultadoActual?.items) {
    return widget.resultadoActual.items.every((i) => !i.valor)
  }

  return false
}
