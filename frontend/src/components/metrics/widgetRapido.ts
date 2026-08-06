import type {
  CampoMetricaMetadataDto,
  MetricaClinicaResponseDto,
  PanelClinicoResponseDto,
  TipoVisualizacion,
} from '../../api/types'
import type { OpcionVisualizacion } from '../dashboard/visualizacionesCompatibles'
import { ETIQUETA_VISUALIZACION_OFRECIDA } from '../dashboard/visualizacionesCompatibles'

/**
 * Qué widget tiene sentido para una métrica ya guardada (Fase 6.9I.4).
 *
 * Se deduce de la operación y del tipo del campo sobre el que opera, con el
 * mismo criterio que `OperacionMetricaUtil` en el backend: si el backend
 * rechaza una serie temporal sobre una distribución, ofrecerla aquí solo
 * produciría un widget en ERROR.
 */

export type FormaResultado = 'ACTUAL' | 'SERIE_TEMPORAL' | 'COMPARATIVA'

export const ETIQUETA_FORMA: Record<FormaResultado, string> = {
  ACTUAL: 'Un solo valor',
  COMPARATIVA: 'Agrupado por categoría',
  SERIE_TEMPORAL: 'Evolución en el tiempo',
}

/** Operaciones cuyo resultado es un reparto de categorías, no un número. */
const OPERACIONES_DE_REPARTO = new Set(['DISTRIBUCION'])

/** Operaciones cuyo resultado es una etiqueta, no un número. */
const OPERACIONES_TEXTUALES = new Set(['CATEGORIA_PRINCIPAL'])

/** Operan sobre un campo que puede ser fecha, y entonces devuelven una fecha. */
const OPERACIONES_EXTREMO = new Set(['MINIMO', 'MAXIMO'])

function campoObjetivo(metrica: MetricaClinicaResponseDto): string | null {
  return metrica.configuracion?.campoValor ?? metrica.configuracion?.campoAgrupacion ?? null
}

/**
 * ¿El resultado es un número que se pueda poner en un eje? Solo entonces tienen
 * sentido la serie temporal y la comparativa.
 */
export function produceValorNumerico(
  metrica: MetricaClinicaResponseDto,
  campos: CampoMetricaMetadataDto[],
): boolean {
  if (OPERACIONES_DE_REPARTO.has(metrica.tipoMetrica)) return false
  if (OPERACIONES_TEXTUALES.has(metrica.tipoMetrica)) return false

  if (OPERACIONES_EXTREMO.has(metrica.tipoMetrica)) {
    const codigo = campoObjetivo(metrica)
    const campo = campos.find((c) => c.codigo === codigo)
    // Mínimo/máximo sobre una fecha devuelven una fecha: no hay eje que valga.
    return campo?.tipoDato !== 'FECHA'
  }

  return true
}

/** Formas de resultado que esta métrica admite, en orden de utilidad. */
export function formasCompatibles(
  metrica: MetricaClinicaResponseDto,
  campos: CampoMetricaMetadataDto[],
): FormaResultado[] {
  if (!produceValorNumerico(metrica, campos)) return ['ACTUAL']
  return ['ACTUAL', 'COMPARATIVA', 'SERIE_TEMPORAL']
}

/** Visualizaciones que tiene sentido ofrecer para una forma concreta. */
export function visualizacionesDeForma(
  metrica: MetricaClinicaResponseDto,
  forma: FormaResultado,
): OpcionVisualizacion[] {
  let valores: TipoVisualizacion[]

  if (forma === 'SERIE_TEMPORAL' || forma === 'COMPARATIVA') {
    valores = visualizacionesDeFormaAgregada(forma)
  } else if (OPERACIONES_DE_REPARTO.has(metrica.tipoMetrica)) {
    // Un reparto sí es un gráfico. El donut se ofrece porque el número real de
    // categorías no se conoce hasta ejecutar; el selector del propio widget lo
    // retira después si resultan ser demasiadas.
    valores = ['BARRAS', 'DONUT', 'TABLA']
  } else {
    // Escalar (número, fecha o etiqueta): solo KPI. "Tabla" renderizaría el
    // mismo componente y sería una opción falsa.
    valores = ['KPI']
  }

  return valores.map((valor) => ({
    valor: valor as OpcionVisualizacion['valor'],
    etiqueta: ETIQUETA_VISUALIZACION_OFRECIDA[valor as OpcionVisualizacion['valor']],
  }))
}

/**
 * Ancho por defecto: un KPI ocupa un cuarto de fila; un reparto o una
 * comparativa, media; una evolución en el tiempo, la fila entera, porque el eje
 * temporal necesita recorrido para leerse.
 *
 * `categorias` afina el caso agrupado: una tabla o muchas categorías necesitan
 * la fila completa para no salir apretadas.
 */
export function anchoRecomendado(
  visualizacion: string,
  forma: FormaResultado,
  categorias?: number | null,
): number {
  // La FORMA manda sobre la visualización, y en este orden.
  //
  // Al revés —comprobando primero `visualizacion === 'KPI'`— un KPI que pasa a
  // COMPARATIVA conservaba ancho 3: cuando se calcula el nuevo ancho, la
  // visualización todavía es la vieja. El ancho lo decide qué se va a dibujar,
  // no lo que se dibujaba antes.
  if (forma === 'SERIE_TEMPORAL') return 12

  if (forma === 'COMPARATIVA') {
    // Una tabla necesita ancho para sus columnas, y por encima de ocho barras
    // las etiquetas dejan de leerse en media fila.
    if (visualizacion === 'TABLA') return 12
    if (categorias != null && categorias > MAX_CATEGORIAS_MEDIA_FILA) return 12
    return 6
  }

  // ACTUAL: solo aquí un KPI es un KPI.
  if (visualizacion === 'KPI') return 3

  return 6
}

/**
 * Visualizaciones de una forma AGREGADA (comparativa o serie temporal).
 *
 * <p>A diferencia de {@link visualizacionesDeForma}, no necesita la métrica:
 * una vez agrupado o puesto en el tiempo, el resultado es una serie de valores
 * y las opciones son las mismas sea cual sea la operación de origen. Se expone
 * aparte para que quien solo tiene el widget (y no su métrica) pueda
 * consultarlas sin duplicar la lista.
 */
export function visualizacionesDeFormaAgregada(
  forma: 'COMPARATIVA' | 'SERIE_TEMPORAL',
): TipoVisualizacion[] {
  return forma === 'SERIE_TEMPORAL' ? ['LINEAS', 'BARRAS', 'TABLA'] : ['BARRAS', 'TABLA']
}

/**
 * Visualización a la que cambiar cuando la actual deja de valer.
 *
 * <p>Un KPI no puede representar una comparativa: si al agrupar se conservara,
 * el widget acabaría en ERROR o pintando un número suelto donde debería haber
 * un gráfico. Se prefiere BARRAS por ser la lectura más directa de un reparto.
 */
export function visualizacionAlCambiarForma(
  visualizacionActual: string,
  forma: FormaResultado,
): string {
  if (forma === 'ACTUAL') return visualizacionActual

  const compatibles = visualizacionesDeFormaAgregada(forma)
  if (compatibles.includes(visualizacionActual as TipoVisualizacion)) {
    return visualizacionActual
  }

  return forma === 'COMPARATIVA' && compatibles.includes('BARRAS') ? 'BARRAS' : compatibles[0]
}

/** A partir de aquí, un gráfico agrupado pide fila completa. */
const MAX_CATEGORIAS_MEDIA_FILA = 8

/**
 * Ancho que debe tomar un widget al cambiar de forma.
 *
 * <p>Solo AMPLÍA: si el usuario le había puesto ancho completo a un KPI, pasar
 * a comparativa no se lo baja a media fila. Recomendar no es deshacer una
 * decisión que alguien tomó a mano.
 */
export function anchoAlCambiarForma(
  anchoActual: number,
  visualizacion: string,
  forma: FormaResultado,
  categorias?: number | null,
): number {
  const recomendado = anchoRecomendado(visualizacion, forma, categorias)
  return Math.max(anchoActual, recomendado)
}

/**
 * El panel principal del dataset: el activo de menor orden. Es el mismo
 * criterio que usa el acceso directo de la página del dataset, para que
 * "añadir al dashboard" y "abrir dashboard" no lleven a paneles distintos.
 */
export function panelPrincipal(paneles: PanelClinicoResponseDto[]): PanelClinicoResponseDto | null {
  if (paneles.length === 0) return null
  return [...paneles].sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0))[0]
}

/** Nombre en pantalla de cada operación. El enum crudo no le dice nada a nadie. */
export const NOMBRE_OPERACION: Record<string, string> = {
  CONTEO: 'Conteo',
  CONTEO_DISTINTO: 'Valores distintos',
  PORCENTAJE: 'Porcentaje',
  COMPLETITUD: 'Completitud',
  PROMEDIO: 'Media',
  MEDIANA: 'Mediana',
  SUMA: 'Suma',
  MINIMO: 'Mínimo',
  MAXIMO: 'Máximo',
  DISTRIBUCION: 'Distribución',
  CATEGORIA_PRINCIPAL: 'Categoría más frecuente',
}

/** Resumen de una métrica para la tarjeta de éxito: qué es y de dónde sale. */
export function describirMetrica(
  metrica: MetricaClinicaResponseDto,
  campos: CampoMetricaMetadataDto[],
): { operacion: string; campo: string | null; forma: string } {
  const codigo = campoObjetivo(metrica)
  const campo = campos.find((c) => c.codigo === codigo)
  const formas = formasCompatibles(metrica, campos)

  return {
    operacion: NOMBRE_OPERACION[metrica.tipoMetrica] ?? metrica.tipoMetrica,
    campo: campo?.etiqueta ?? codigo,
    forma: OPERACIONES_DE_REPARTO.has(metrica.tipoMetrica)
      ? 'Reparto por categorías'
      : formas.length > 1
        ? 'Valor único (puede agruparse o verse en el tiempo)'
        : 'Valor único',
  }
}
