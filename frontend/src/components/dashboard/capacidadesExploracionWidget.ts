import type { DashboardWidgetDto } from '../../api/types'
import type { CampoFiltroCategoria } from './camposFiltroDashboard'

/**
 * Fase 6.9G.2 — qué exploración local admite CADA widget.
 *
 * Centralizado a propósito: las reglas dependen de restricciones reales del
 * backend (ver más abajo) y repartirlas en condicionales sueltos dentro del
 * renderer garantizaría que tarde o temprano se ofrezca una opción que acaba
 * en error técnico.
 */

export type ModoExploracion = 'ACTUAL' | 'CATEGORIA' | 'INDIVIDUO'

export interface CapacidadesExploracion {
  soportaCategoria: boolean
  soportaIndividuo: boolean
  soportaCambioVisualizacion: boolean
  camposAgrupables: CampoFiltroCategoria[]
  /** Por qué no se ofrece "Categoría", en lenguaje de usuario. Null si sí se ofrece. */
  motivoNoDisponible: string | null
}

/** El tipo de métrica no viaja suelto en DashboardWidgetDto: está en el resultado que traiga. */
export function tipoMetricaDeWidget(widget: DashboardWidgetDto): string | null {
  return (
    widget.resultadoActual?.tipoMetrica ??
    widget.comparativa?.tipoMetrica ??
    widget.serieTemporal?.tipoMetrica ??
    null
  )
}

export function capacidadesExploracionWidget(
  widget: DashboardWidgetDto,
  camposAgrupables: CampoFiltroCategoria[],
  hayCampoIndividuo: boolean,
): CapacidadesExploracion {
  const sinCapacidades: CapacidadesExploracion = {
    soportaCategoria: false,
    soportaIndividuo: false,
    soportaCambioVisualizacion: false,
    camposAgrupables: [],
    motivoNoDisponible: null,
  }

  // Un widget que no se ha podido calcular no puede explorarse: cualquier
  // control que ofreciéramos fallaría igual.
  if (widget.estado === 'ERROR') {
    return { ...sinCapacidades, motivoNoDisponible: 'Este indicador no se ha podido calcular.' }
  }

  const tipoMetrica = tipoMetricaDeWidget(widget)

  // DISTRIBUCION no admite comparativa ni serie temporal en el backend
  // (MetricaAnaliticaServiceImpl.validarTipoPermitido la rechaza), y su
  // agrupación vive dentro de la definición de la métrica: cambiarla sería
  // modificar la métrica, no explorar. Sí admite acotarse a un individuo.
  const esDistribucion = tipoMetrica === 'DISTRIBUCION'

  // Para reconstruir una serie temporal hace falta la granularidad con la que
  // se calculó; si el widget no la trae, no se puede reejecutar.
  const esSerie = widget.tipoResultado === 'SERIE_TEMPORAL'
  const tieneGranularidad = Boolean(widget.serieTemporal?.granularidad)

  const soportaCategoria = !esDistribucion && camposAgrupables.length > 0
  const soportaIndividuo = hayCampoIndividuo && (!esSerie || tieneGranularidad)

  let motivoNoDisponible: string | null = null
  if (!soportaCategoria) {
    if (esDistribucion) {
      motivoNoDisponible = 'Este indicador ya reparte los datos por una categoría definida en la métrica.'
    } else if (camposAgrupables.length === 0) {
      motivoNoDisponible = 'Este conjunto de datos no tiene campos por los que agrupar.'
    }
  }

  return {
    soportaCategoria,
    soportaIndividuo,
    soportaCambioVisualizacion: true,
    camposAgrupables: soportaCategoria ? camposAgrupables : [],
    motivoNoDisponible,
  }
}
