import type { CampoMetricaMetadataDto, PanelClinicoResponseDto, PanelMetricaConfiguracionWidgetRequestDto } from '../../api/types'
import { crearMetrica, obtenerMetadataMetricas, previewMetrica } from '../../api/metricasApi'
import { actualizarConfiguracionWidget, anadirWidget, crearPanel, listarPaneles } from '../../api/panelesApi'
import { proponerMetricas } from './reglasMetricas'

export type ClavePasoDashboardInicial =
  | 'revisar-campos'
  | 'preparar-indicadores'
  | 'crear-panel'
  | 'anadir-widgets'
  | 'abrir-dashboard'

export type EstadoPasoDashboardInicial = 'pendiente' | 'en-curso' | 'correcto' | 'error'

export interface PasoProgresoDashboardInicial {
  clave: ClavePasoDashboardInicial
  etiqueta: string
  estado: EstadoPasoDashboardInicial
}

export const PASOS_DASHBOARD_INICIAL: PasoProgresoDashboardInicial[] = [
  { clave: 'revisar-campos', etiqueta: 'Revisando campos del dataset', estado: 'pendiente' },
  { clave: 'preparar-indicadores', etiqueta: 'Preparando indicadores', estado: 'pendiente' },
  { clave: 'crear-panel', etiqueta: 'Creando panel inicial', estado: 'pendiente' },
  { clave: 'anadir-widgets', etiqueta: 'Añadiendo widgets', estado: 'pendiente' },
  { clave: 'abrir-dashboard', etiqueta: 'Abriendo dashboard', estado: 'pendiente' },
]

export interface ResultadoDashboardInicial {
  panelId: number | null
  metricasCreadas: number
  metricasFallidas: string[]
  widgetsCreados: number
  widgetsFallidos: string[]
  /** Si no es null, la creación se abortó: mostrar este mensaje amable y ofrecer modo avanzado. */
  errorPanel: string | null
}

const CODIGO_BASE_PANEL = 'dashboard_inicial'
const NOMBRE_PANEL = 'Dashboard inicial'
const DESCRIPCION_PANEL = 'Dashboard generado automáticamente desde el archivo importado.'
const MAX_INTENTOS_CODIGO_PANEL = 3

type OnProgreso = (paso: ClavePasoDashboardInicial, estado: EstadoPasoDashboardInicial) => void

function mensajeErrorAmable(err: unknown): string {
  return err instanceof Error ? err.message : 'Error inesperado.'
}

/** Busca un panel activo ya existente con código dashboard_inicial (o su sufijo _2/_3). */
export async function buscarDashboardInicialExistente(datasetId: number): Promise<PanelClinicoResponseDto | null> {
  const paneles = await listarPaneles(datasetId)
  return paneles.find((p) => p.activo && p.codigo.startsWith(CODIGO_BASE_PANEL)) ?? null
}

/**
 * Comprueba si el dataset tiene al menos un registro clínico importado, sin
 * crear nada persistido: reutiliza el endpoint de previsualización de
 * métricas con un conteo sin filtros (la misma configuración que la métrica
 * "Total de registros" del dashboard inicial). Si la previsualización falla
 * (p. ej. el dataset todavía no tiene campos), se asume que no hay datos: es
 * más seguro no ofrecer "Crear dashboard inicial" que ofrecerlo sin base.
 */
export async function datasetTieneRegistros(datasetId: number): Promise<boolean> {
  try {
    const resultado = await previewMetrica(datasetId, {
      metrica: {
        codigo: 'comprobacion_total_registros',
        nombre: 'Comprobación de registros',
        tipoMetrica: 'CONTEO',
        configuracion: { filtros: [] },
      },
    })
    return (resultado.valor ?? 0) > 0
  } catch {
    return false
  }
}

// Reintenta con sufijo _2/_3 solo si el fallo es por código duplicado (defensivo:
// la vía principal ya comprueba con buscarDashboardInicialExistente antes de llamar).
async function crearPanelConSufijo(datasetId: number, intento = 1): Promise<PanelClinicoResponseDto> {
  const codigo = intento === 1 ? CODIGO_BASE_PANEL : `${CODIGO_BASE_PANEL}_${intento}`
  try {
    return await crearPanel(datasetId, { codigo, nombre: NOMBRE_PANEL, descripcion: DESCRIPCION_PANEL, orden: 1 })
  } catch (err) {
    const mensaje = mensajeErrorAmable(err).toLowerCase()
    if (intento < MAX_INTENTOS_CODIGO_PANEL && mensaje.includes('ya existe un panel activo')) {
      return crearPanelConSufijo(datasetId, intento + 1)
    }
    throw err
  }
}

/**
 * Crea el dashboard inicial completo: lee la metadata real del dataset,
 * propone métricas (reglasMetricas.ts), crea el panel, añade los widgets y
 * configura el de evolución temporal. Un fallo de una métrica u widget
 * concretos no aborta el resto; un fallo al crear el panel sí aborta todo.
 */
export async function crearDashboardInicial(
  datasetId: number,
  onProgreso: OnProgreso,
): Promise<ResultadoDashboardInicial> {
  const resultado: ResultadoDashboardInicial = {
    panelId: null,
    metricasCreadas: 0,
    metricasFallidas: [],
    widgetsCreados: 0,
    widgetsFallidos: [],
    errorPanel: null,
  }

  onProgreso('revisar-campos', 'en-curso')
  let campos: CampoMetricaMetadataDto[]
  try {
    const metadata = await obtenerMetadataMetricas(datasetId)
    campos = metadata.campos
    onProgreso('revisar-campos', 'correcto')
  } catch (err) {
    onProgreso('revisar-campos', 'error')
    resultado.errorPanel = mensajeErrorAmable(err)
    return resultado
  }

  onProgreso('preparar-indicadores', 'en-curso')
  const propuestas = proponerMetricas(campos)
  const idsPorCodigo = new Map<string, number>()
  for (const propuesta of propuestas) {
    try {
      const metrica = await crearMetrica(datasetId, {
        codigo: propuesta.codigo,
        nombre: propuesta.nombre,
        tipoMetrica: propuesta.tipoMetrica,
        configuracion: propuesta.configuracion,
      })
      idsPorCodigo.set(propuesta.codigo, metrica.id)
      resultado.metricasCreadas += 1
    } catch {
      resultado.metricasFallidas.push(propuesta.nombre)
    }
  }
  onProgreso('preparar-indicadores', resultado.metricasCreadas > 0 ? 'correcto' : 'error')
  if (resultado.metricasCreadas === 0) {
    resultado.errorPanel = 'No se pudo preparar ningún indicador.'
    return resultado
  }

  onProgreso('crear-panel', 'en-curso')
  let panelId: number
  try {
    const panel = await crearPanelConSufijo(datasetId)
    panelId = panel.id
    resultado.panelId = panelId
    onProgreso('crear-panel', 'correcto')
  } catch (err) {
    onProgreso('crear-panel', 'error')
    resultado.errorPanel = mensajeErrorAmable(err)
    return resultado
  }

  onProgreso('anadir-widgets', 'en-curso')
  let orden = 1
  for (const propuesta of propuestas) {
    const metricaId = idsPorCodigo.get(propuesta.codigo)
    if (metricaId === undefined) continue // su métrica falló antes; se salta el widget.
    try {
      const widget = await anadirWidget(panelId, {
        metricaId,
        tipoVisualizacion: propuesta.widget.tipoVisualizacion,
        orden,
        ancho: propuesta.widget.ancho,
      })
      if (propuesta.widget.requiereGranularidad) {
        const payload: PanelMetricaConfiguracionWidgetRequestDto = {
          tipoResultado: 'SERIE_TEMPORAL',
          configuracionWidget: {
            granularidad: 'MES',
            campoFecha: null,
            campoSegmentacion: null,
            campoAgrupacion: null,
          },
        }
        await actualizarConfiguracionWidget(panelId, widget.id, payload)
      }
      resultado.widgetsCreados += 1
    } catch {
      resultado.widgetsFallidos.push(propuesta.nombre)
    }
    orden += 1
  }
  onProgreso('anadir-widgets', resultado.widgetsCreados > 0 ? 'correcto' : 'error')

  onProgreso('abrir-dashboard', 'correcto')
  return resultado
}
