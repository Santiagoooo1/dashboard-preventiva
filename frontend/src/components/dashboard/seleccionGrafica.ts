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

export interface SeleccionGrafica {
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

  // Una selección temporal exige convertir un periodo en un rango real de
  // fechas; es el objeto de la fase 6.9H.2.
  if (widget.tipoResultado === 'SERIE_TEMPORAL') return no('La selección por periodos llegará más adelante.')

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

/** Dos selecciones son la misma si coinciden campo y valor técnico. */
export function esMismaSeleccion(
  seleccion: SeleccionGrafica | null,
  campo: string,
  valorOriginal: string,
): boolean {
  return seleccion !== null && seleccion.campo === campo && seleccion.valorOriginal === valorOriginal
}

/**
 * Filtro que viaja al backend. Usa SIEMPRE el valor técnico: enviar "Sí" en
 * lugar de "true" no encontraría ningún registro.
 */
export function filtroDeSeleccion(seleccion: SeleccionGrafica): FiltroMetricaDto {
  return { campo: seleccion.campo, operador: seleccion.operador, valor: seleccion.valorOriginal }
}

/** Filtros globales AND selección gráfica, en el orden que espera el endpoint. */
export function combinarFiltros(
  filtrosGlobales: FiltroMetricaDto[],
  seleccion: SeleccionGrafica | null,
): FiltroMetricaDto[] {
  return seleccion ? [...filtrosGlobales, filtroDeSeleccion(seleccion)] : [...filtrosGlobales]
}

/**
 * Alterna: pulsar el valor ya seleccionado lo quita, cualquier otro lo
 * sustituye (solo hay una selección activa en todo el dashboard).
 */
export function alternarSeleccion(
  actual: SeleccionGrafica | null,
  nueva: SeleccionGrafica,
): SeleccionGrafica | null {
  return esMismaSeleccion(actual, nueva.campo, nueva.valorOriginal) ? null : nueva
}

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
