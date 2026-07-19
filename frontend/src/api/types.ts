// Tipos que reflejan los DTOs reales del backend (Fase 5.1 / Fase 5.2).
// Se mantienen deliberadamente alineados campo a campo con las clases Java
// correspondientes para detectar desajustes de integración en compilación.

export interface ApiErrorResponseDto {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors: { campo: string; mensaje: string }[] | null
}

// --- Fase 5.2: catálogo global ---

export interface EstructuraConfiguracionMetricaDto {
  camposRequeridos: string[]
  camposOpcionales: string[]
}

export interface TipoMetricaCatalogoDto {
  codigo: string
  nombre: string
  descripcion: string | null
  requiereCampoValor: boolean
  requiereCampoAgrupacion: boolean
  permiteFiltros: boolean
  permiteSerieTemporal: boolean
  permiteComparativa: boolean
  estructuraConfiguracion: EstructuraConfiguracionMetricaDto
  ejemploConfiguracion: unknown
}

export interface OperadorFiltroCatalogoDto {
  codigo: string
  nombre: string
  tiposDatoCompatibles: string[]
  requiereValor: boolean
  requiereLista: boolean
}

export interface TipoVisualizacionCatalogoDto {
  codigo: string
  nombre: string
  tipoResultadoPorDefecto: string | null
}

export interface OpcionCatalogoDto {
  codigo: string
  nombre: string
  descripcion: string | null
}

export interface ResolucionTipoResultadoDto {
  tipoVisualizacion: string
  tipoResultadoSiDistribucion: string
  tipoResultadoConAgrupacion: string
  tipoResultadoSinAgrupacion: string
}

export interface ReglasCompatibilidadDto {
  resolucionTipoResultadoWidget: ResolucionTipoResultadoDto[]
}

export interface CatalogoFrontendResponseDto {
  tipoMetricas: TipoMetricaCatalogoDto[]
  operadoresFiltro: OperadorFiltroCatalogoDto[]
  tipoVisualizaciones: TipoVisualizacionCatalogoDto[]
  tipoResultadoWidget: OpcionCatalogoDto[]
  granularidades: OpcionCatalogoDto[]
  tiposDato: OpcionCatalogoDto[]
  politicasCampoFaltante: OpcionCatalogoDto[]
  reglasCompatibilidad: ReglasCompatibilidadDto
}

// --- Fase 5.2: metadata de dataset ---

export interface DatasetClinicoResponseDto {
  id: number
  codigo: string
  nombre: string
  descripcion: string | null
  hospitalId: number | null
  hospitalNombre: string | null
  activo: boolean
}

export type TipoDato = 'TEXTO' | 'ENTERO' | 'DECIMAL' | 'FECHA' | 'BOOLEANO'

// --- Fase 6.3: CRUD de datasets y campos ---

export interface DatasetClinicoRequestDto {
  codigo: string
  nombre: string
  descripcion?: string | null
  hospitalId?: number | null
}

export interface CampoClinicoRequestDto {
  codigo: string
  etiqueta: string
  tipoDato: TipoDato
  esComun: boolean
  obligatorio: boolean
  orden?: number | null
}

export interface CampoClinicoResponseDto {
  id: number
  datasetId: number
  codigo: string
  etiqueta: string
  tipoDato: string
  esComun: boolean
  obligatorio: boolean
  orden: number | null
  activo: boolean
}

export interface CampoRolesDto {
  filtrable: boolean
  agrupable: boolean
  numerico: boolean
  fecha: boolean
}

export interface CampoClinicoMetadataDto {
  id: number
  codigo: string
  etiqueta: string
  tipoDato: string
  esComun: boolean
  obligatorio: boolean
  activo: boolean
  roles: CampoRolesDto
}

// --- Fase 4.1: métricas configurables ---

export type TipoMetrica = 'CONTEO' | 'PORCENTAJE' | 'PROMEDIO' | 'SUMA' | 'DISTRIBUCION'

export interface FiltroMetricaDto {
  campo: string
  operador: string
  valor: unknown
}

export interface FiltroGrupoDto {
  filtros: FiltroMetricaDto[]
}

export interface ConfiguracionMetricaDto {
  filtros?: FiltroMetricaDto[] | null
  numerador?: FiltroGrupoDto | null
  denominador?: FiltroGrupoDto | null
  campoValor?: string | null
  campoAgrupacion?: string | null
}

export interface MetricaClinicaRequestDto {
  codigo: string
  nombre: string
  descripcion?: string | null
  tipoMetrica: TipoMetrica
  configuracion: ConfiguracionMetricaDto
  unidad?: string | null
  decimales?: number | null
  orden?: number | null
}

export interface PreviewMetricaRequestDto {
  metrica: MetricaClinicaRequestDto
  fechaDesde?: string | null
  fechaHasta?: string | null
}

export interface EjecucionMetricaRequestDto {
  fechaDesde?: string | null
  fechaHasta?: string | null
}

export interface MetricaClinicaResponseDto {
  id: number
  datasetId: number
  codigo: string
  nombre: string
  descripcion: string | null
  tipoMetrica: string
  configuracion: ConfiguracionMetricaDto
  unidad: string | null
  decimales: number
  orden: number
  activa: boolean
}

// --- Fase 5.2: metadata para construir métricas ---

export interface CampoMetricaMetadataDto {
  codigo: string
  etiqueta: string
  tipoDato: string
  esComun: boolean
  operadoresCompatibles: string[]
  utilizableComoCampoValor: boolean
  utilizableComoCampoAgrupacion: boolean
  utilizableComoCampoFecha: boolean
}

export interface MetadataMetricasResponseDto {
  dataset: DatasetClinicoResponseDto
  campos: CampoMetricaMetadataDto[]
}

export interface PanelClinicoResponseDto {
  id: number
  datasetId: number
  codigo: string
  nombre: string
  descripcion: string | null
  orden: number
  activo: boolean
}

export interface PlantillaImportacionResponseDto {
  id: number
  datasetId: number
  datasetCodigo: string
  nombre: string
  descripcion: string | null
  origen: string | null
  filaCabecera: number | null
  activa: boolean
}

export interface ResumenConfiguracionDatasetDto {
  totalCampos: number
  totalMetricas: number
  totalPaneles: number
  totalPlantillasImportacion: number
}

export interface DatasetFrontendMetadataResponseDto {
  dataset: DatasetClinicoResponseDto
  campos: CampoClinicoMetadataDto[]
  camposFiltrables: string[]
  camposNumericos: string[]
  camposAgrupables: string[]
  camposFecha: string[]
  metricas: MetricaClinicaResponseDto[]
  paneles: PanelClinicoResponseDto[]
  plantillasImportacion: PlantillaImportacionResponseDto[]
  resumenConfiguracion: ResumenConfiguracionDatasetDto
}

// --- Fase 5.1: dashboard de panel ---

export type TipoResultado = 'ACTUAL' | 'SERIE_TEMPORAL' | 'COMPARATIVA'
export type EstadoWidget = 'OK' | 'ERROR'

export interface ItemDistribucionDto {
  etiqueta: string
  valor: number
}

export interface ResultadoMetricaResponseDto {
  metricaId: number
  codigo: string
  nombre: string
  tipoMetrica: string
  valor: number | null
  unidad: string | null
  totalNumerador: number | null
  totalDenominador: number | null
  items: ItemDistribucionDto[] | null
}

export interface PuntoSerieDto {
  periodo: string
  fechaInicio: string
  fechaFin: string
  valor: number | null
  totalNumerador: number | null
  totalDenominador: number | null
}

export interface SerieSegmentadaDto {
  etiqueta: string
  puntos: PuntoSerieDto[]
}

export interface SerieTemporalResponseDto {
  metricaId: number
  codigo: string
  tipoMetrica: string
  granularidad: string
  segmentadoPor: string | null
  puntos: PuntoSerieDto[] | null
  series: SerieSegmentadaDto[] | null
}

export interface ItemComparativaDto {
  etiqueta: string
  valor: number | null
  totalNumerador: number | null
  totalDenominador: number | null
}

export interface ComparativaResponseDto {
  metricaId: number
  codigo: string
  tipoMetrica: string
  agrupadoPor: string
  items: ItemComparativaDto[]
}

export interface DashboardWidgetErrorDto {
  mensaje: string
}

export interface DashboardWidgetDto {
  panelMetricaId: number
  metricaId: number
  codigo: string
  titulo: string
  descripcion: string | null
  tipoVisualizacion: string
  tipoResultado: TipoResultado
  orden: number
  ancho: number
  estado: EstadoWidget
  resultadoActual: ResultadoMetricaResponseDto | null
  serieTemporal: SerieTemporalResponseDto | null
  comparativa: ComparativaResponseDto | null
  error: DashboardWidgetErrorDto | null
}

export interface DashboardPanelInfoDto {
  id: number
  codigo: string
  nombre: string
  descripcion: string | null
}

export interface DashboardDatasetInfoDto {
  id: number
  codigo: string
  nombre: string
}

export interface DashboardFiltrosAplicadosDto {
  fechaDesde: string | null
  fechaHasta: string | null
  granularidad: string | null
}

export interface DashboardPanelResumenDto {
  totalWidgets: number
  widgetsOk: number
  widgetsConError: number
}

export interface DashboardPanelResponseDto {
  panel: DashboardPanelInfoDto
  dataset: DashboardDatasetInfoDto
  filtrosAplicados: DashboardFiltrosAplicadosDto
  widgets: DashboardWidgetDto[]
  resumen: DashboardPanelResumenDto
}
