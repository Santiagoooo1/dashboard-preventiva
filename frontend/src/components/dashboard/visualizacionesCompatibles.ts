import type { DashboardWidgetDto } from '../../api/types'

export type TipoVisualizacionOfrecido = 'KPI' | 'LINEAS' | 'BARRAS' | 'DONUT' | 'TABLA'

export interface OpcionVisualizacion {
  valor: TipoVisualizacionOfrecido
  etiqueta: string
}

export const ETIQUETA_VISUALIZACION_OFRECIDA: Record<TipoVisualizacionOfrecido, string> = {
  KPI: 'Indicador',
  LINEAS: 'Línea',
  BARRAS: 'Barras',
  DONUT: 'Donut',
  TABLA: 'Tabla',
}

// A partir de este número de categorías, un donut deja de ser legible de un
// vistazo (coincide con el umbral ya usado para agrupar en "Otros" en
// PieChartWidget, con margen); mejor no ofrecerlo como opción.
const MAX_CATEGORIAS_DONUT = 8

/**
 * Qué tipos de visualización tiene sentido ofrecer para ESTE widget, a partir
 * de la FORMA de los datos que ya está devolviendo ahora mismo (valor único,
 * distribución categórica, comparativa o serie temporal) — no de la
 * configuración interna de la métrica (tipoMetrica/campoAgrupacion).
 *
 * Es una elección deliberada: el backend decide qué "tipoResultado" calcular
 * a partir de tipoVisualizacion + tipoMetrica + configuración (ver
 * `DashboardPanelServiceImpl.resolverTipoResultado`), y algunas combinaciones
 * son peligrosas si se ofrecen a ciegas:
 *   - Elegir "Línea" para una métrica sin fecha/granularidad configurada
 *     provoca un widget en ERROR ("no se ha especificado granularidad").
 *   - Elegir "Barras"/"Donut" para un valor único sin agrupación no rompe
 *     nada, pero cae al mismo número suelto que un KPI — una opción que
 *     parece un gráfico y en realidad no lo es.
 * Basar la oferta en la forma ya calculada evita ambos casos sin necesitar
 * conocer la configuración interna de la métrica ni tocar el backend.
 */
export function visualizacionesCompatibles(widget: DashboardWidgetDto): OpcionVisualizacion[] {
  if (widget.estado === 'ERROR') return []

  const itemsDistribucion = widget.tipoResultado === 'ACTUAL' ? (widget.resultadoActual?.items ?? null) : null
  const valorUnico =
    widget.tipoResultado === 'ACTUAL' &&
    widget.resultadoActual?.valor !== null &&
    widget.resultadoActual?.valor !== undefined &&
    !itemsDistribucion
  const itemsComparativa = widget.tipoResultado === 'COMPARATIVA' ? (widget.comparativa?.items ?? null) : null
  const esSerie = widget.tipoResultado === 'SERIE_TEMPORAL' && widget.serieTemporal != null

  let valores: TipoVisualizacionOfrecido[] = []
  if (valorUnico) {
    // Solo KPI. "Tabla" figuraba aquí como segunda opción, pero para un valor
    // escalar NO produce una tabla: `cuerpoWidget` renderiza el mismo
    // <KpiWidget> (con `compacto`), y la vista de respaldo `WidgetActual`
    // tampoco pinta filas cuando no hay `items` — imprime el mismo número.
    // Eran dos etiquetas para una única representación, y por eso los KPI
    // seguían mostrando el selector "Gráfico" sin ofrecer ninguna alternativa.
    valores = ['KPI']
  } else if (itemsDistribucion) {
    valores = ['BARRAS']
    if (itemsDistribucion.length <= MAX_CATEGORIAS_DONUT) valores.push('DONUT')
    valores.push('TABLA')
  } else if (itemsComparativa) {
    valores = ['BARRAS', 'TABLA']
  } else if (esSerie) {
    valores = ['LINEAS', 'BARRAS', 'TABLA']
  }

  return valores.map((valor) => ({ valor, etiqueta: ETIQUETA_VISUALIZACION_OFRECIDA[valor] }))
}

/**
 * Opciones realmente distintas entre sí. `visualizacionesCompatibles` ya
 * construye la lista sin repetir, pero esta función es la única fuente de
 * verdad para "¿hay una decisión que tomar aquí?": deduplica por código
 * normalizado y descarta vacíos, de modo que el selector "Gráfico" solo se
 * pinte cuando existan >= 2 alternativas reales. Sin esto, un KPI mostraba
 * "Gráfico: [Indicador]" — un desplegable con una sola opción, que aparenta
 * una elección inexistente.
 */
export function visualizacionesUnicas(opciones: OpcionVisualizacion[]): OpcionVisualizacion[] {
  const vistas = new Set<string>()
  const unicas: OpcionVisualizacion[] = []

  for (const opcion of opciones) {
    if (!opcion?.valor) continue
    const clave = normalizarTipoVisualizacion(opcion.valor)
    if (vistas.has(clave)) continue
    vistas.add(clave)
    unicas.push(opcion)
  }

  return unicas
}

/**
 * Normaliza el tipoVisualizacion persistido (puede ser TARJETA/PIE, alias
 * históricos que el selector ya no usa pero que el backend sigue aceptando)
 * al valor equivalente que sí ofrece este selector, para marcarlo como
 * seleccionado sin necesidad de tocar los datos guardados.
 */
export function normalizarTipoVisualizacion(actual: string): TipoVisualizacionOfrecido {
  if (actual === 'TARJETA') return 'KPI'
  if (actual === 'PIE') return 'DONUT'
  if (actual === 'KPI' || actual === 'LINEAS' || actual === 'BARRAS' || actual === 'DONUT' || actual === 'TABLA') {
    return actual
  }
  return 'TABLA'
}

/** Cómo se está calculando hoy un widget, en lenguaje de usuario (no el enum crudo ACTUAL/SERIE_TEMPORAL/COMPARATIVA). */
export const ETIQUETA_TIPO_RESULTADO: Record<string, string> = {
  ACTUAL: 'Valor único',
  SERIE_TEMPORAL: 'Evolución en el tiempo',
  COMPARATIVA: 'Agrupado por categoría',
}

export type PlanResultado = 'UNICO' | 'AGRUPADO' | 'SERIE'

/**
 * Misma idea que `visualizacionesCompatibles`, pero para cuando todavía no
 * existe ningún widget que inspeccionar (se está creando uno desde cero, en
 * el asistente de "Nueva métrica"): aquí no hay datos reales que mirar, así
 * que se predice a partir de lo que el usuario ha elegido en el paso
 * "¿Cómo quieres verlo?" (un valor único, agrupado por categoría, o una
 * evolución en el tiempo). Las mismas reglas que `visualizacionesCompatibles`
 * (sin donut para "agrupado": ver el porqué en ese comentario).
 */
export function visualizacionesSegunPlan(plan: PlanResultado): OpcionVisualizacion[] {
  const valores: TipoVisualizacionOfrecido[] =
    plan === 'UNICO' ? ['KPI', 'TABLA'] : plan === 'AGRUPADO' ? ['BARRAS', 'TABLA'] : ['LINEAS', 'BARRAS', 'TABLA']
  return valores.map((valor) => ({ valor, etiqueta: ETIQUETA_VISUALIZACION_OFRECIDA[valor] }))
}
