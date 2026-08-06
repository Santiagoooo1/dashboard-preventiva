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

export type EstadoDatasetClinico = 'BORRADOR' | 'VALIDANDO' | 'ACTIVO' | 'ARCHIVADO' | 'DESCARTADO'

export interface DatasetClinicoResponseDto {
  id: number
  codigo: string
  nombre: string
  descripcion: string | null
  hospitalId: number | null
  hospitalNombre: string | null
  activo: boolean
  estadoDataset: EstadoDatasetClinico | string
}

export type TipoDato = 'TEXTO' | 'ENTERO' | 'DECIMAL' | 'FECHA' | 'BOOLEANO'

// --- Fase 6.3: CRUD de datasets y campos ---

export interface DatasetClinicoRequestDto {
  codigo: string
  nombre: string
  descripcion?: string | null
  hospitalId?: number | null
  /** Opcional: si no se indica, el backend crea el dataset como ACTIVO. */
  estadoDataset?: EstadoDatasetClinico | null
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

export type TipoMetrica =
  | 'CONTEO'
  | 'PORCENTAJE'
  | 'PROMEDIO'
  | 'SUMA'
  | 'DISTRIBUCION'
  // --- Fase 6.9I.2: capacidades genéricas del motor ---
  | 'CONTEO_DISTINTO'
  | 'COMPLETITUD'
  | 'MEDIANA'
  | 'MINIMO'
  | 'MAXIMO'
  | 'CATEGORIA_PRINCIPAL'

/** Qué hacer con los registros sin valor. Por defecto se incluyen como «Sin dato». */
export type TratamientoNulos = 'EXCLUIR' | 'INCLUIR_COMO_CATEGORIA'

/** Cómo se interpreta analíticamente una columna. */
export type RolAnaliticoCampo =
  | 'IDENTIFICADOR'
  | 'BOOLEANO'
  | 'CATEGORICO'
  | 'NUMERICO'
  | 'FECHA'
  | 'TEXTO_LIBRE'

/** OK, o el motivo por el que no hay valor. Nunca NaN. */
export type EstadoResultadoMetrica = 'OK' | 'SIN_BASE_EVALUABLE'

export interface FiltroMetricaDto {
  campo: string
  operador: string
  valor: unknown
}

export interface FiltroGrupoDto {
  filtros: FiltroMetricaDto[]
}

export interface ConfiguracionMetricaDto {
  /** Filtros base: acotan la población y, en PORCENTAJE, también numerador y denominador. */
  filtros?: FiltroMetricaDto[] | null
  numerador?: FiltroGrupoDto | null
  denominador?: FiltroGrupoDto | null
  campoValor?: string | null
  campoAgrupacion?: string | null
  // --- Fase 6.9I.2 ---
  tratamientoNulos?: TratamientoNulos | null
  etiquetaNumerador?: string | null
  etiquetaDenominador?: string | null
  /** Top N de una distribución; el resto se agrupa en «Otros» (solo presentación). */
  maxCategorias?: number | null
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
  /** Filtros aplicados a ESTA ejecución (globales del dashboard y/o locales del widget). */
  filtrosGlobales?: FiltroMetricaDto[] | null
}

export interface ComparativaRequestDto {
  fechaDesde?: string | null
  fechaHasta?: string | null
  campoAgrupacion: string
  filtrosGlobales?: FiltroMetricaDto[] | null
}

export interface SerieTemporalRequestDto {
  fechaDesde?: string | null
  fechaHasta?: string | null
  granularidad: Granularidad
  campoFecha?: string | null
  campoSegmentacion?: string | null
  filtrosGlobales?: FiltroMetricaDto[] | null
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

// --- Fase 6.6: importación de datos ---

export type OrigenImportacion = 'EXCEL' | 'CSV'

export interface PlantillaImportacionRequestDto {
  nombre: string
  descripcion?: string | null
  origen: OrigenImportacion
  filaCabecera?: number | null
}

export interface MapeoCampoImportacionRequestDto {
  nombreColumnaOrigen: string
  campoClinicoId: number
  tipoDato: TipoDato
  obligatorio: boolean
  politicaCampoFaltante?: string | null
  valorPorDefecto?: string | null
  orden?: number | null
}

export interface MapeoCampoImportacionResponseDto {
  id: number
  plantillaId: number
  nombreColumnaOrigen: string
  campoClinicoId: number
  campoClinicoCodigo: string
  campoClinicoEtiqueta: string
  tipoDato: string
  obligatorio: boolean
  politicaCampoFaltante: string | null
  valorPorDefecto: string | null
  orden: number | null
  activo: boolean
}

export interface ColumnaDetectadaResponseDto {
  indiceColumna: number
  nombreOriginal: string
  nombreNormalizado: string
}

export interface DeteccionColumnasResponseDto {
  nombreArchivo: string
  totalColumnas: number
  columnas: ColumnaDetectadaResponseDto[]
}

export interface ColumnaMapeadaDto {
  indiceColumna: number
  nombreColumna: string
  reconocida: boolean
  campoClinicoCodigo: string | null
  tipoDato: string | null
}

export interface ValidacionImportacionGenericaResponseDto {
  nombreArchivo: string
  plantillaId: number
  datasetId: number
  totalColumnasDetectadas: number
  totalColumnasReconocidas: number
  totalColumnasNoReconocidas: number
  columnasDetectadas: ColumnaMapeadaDto[]
  columnasNoReconocidas: string[]
  camposObligatoriosFaltantes: string[]
  valida: boolean
  importable: boolean
  advertencias: string[] | null
  resumen: string
}

export interface ErrorFilaImportacionGenericaDto {
  numeroFila: number | null
  nombreColumna: string | null
  valorOriginal: string | null
  tipoError: string
  severidad: string
  mensaje: string
}

export interface ValidacionFilasImportacionGenericaResponseDto {
  nombreArchivo: string
  plantillaId: number
  datasetId: number
  indiceHoja: number | null
  filaCabecera: number | null
  totalFilasLeidas: number
  filasValidas: number
  filasConError: number
  filasConAdvertencia: number
  errores: ErrorFilaImportacionGenericaDto[]
  erroresBloqueantes: ErrorFilaImportacionGenericaDto[]
  advertencias: ErrorFilaImportacionGenericaDto[]
  totalAdvertencias: number
  valida: boolean
  importable: boolean
  resumen: string
}

export interface ImportacionGenericaResponseDto {
  importacionId: number
  nombreArchivo: string
  plantillaId: number
  datasetId: number
  filasLeidas: number
  filasImportadas: number
  filasConError: number
  totalAdvertencias: number
  estado: string
  mensaje: string
}

export interface ErrorImportacionGenericaResponseDto {
  id: number
  numeroFila: number | null
  nombreColumna: string | null
  valorOriginal: string | null
  tipoError: string
  severidad: string
  mensaje: string
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

// --- Fase 6.4: CRUD de paneles y widgets ---

export type TipoVisualizacion = 'KPI' | 'TARJETA' | 'TABLA' | 'BARRAS' | 'LINEAS' | 'DONUT' | 'PIE'
export type Granularidad = 'MES' | 'TRIMESTRE' | 'ANIO'

export interface PanelClinicoRequestDto {
  codigo: string
  nombre: string
  descripcion?: string | null
  orden?: number | null
}

export interface PanelMetricaRequestDto {
  metricaId: number
  tituloPersonalizado?: string | null
  descripcionPersonalizada?: string | null
  tipoVisualizacion: TipoVisualizacion
  orden?: number | null
  ancho?: number | null
}

export interface ConfiguracionWidgetDto {
  granularidad?: Granularidad | null
  campoFecha?: string | null
  campoSegmentacion?: string | null
  campoAgrupacion?: string | null
}

export interface PanelMetricaResponseDto {
  id: number
  panelId: number
  metricaId: number
  metricaCodigo: string
  metricaNombre: string
  tituloPersonalizado: string | null
  descripcionPersonalizada: string | null
  tipoVisualizacion: string
  orden: number
  ancho: number
  activa: boolean
  tipoResultadoWidget: string | null
  configuracionWidget: ConfiguracionWidgetDto | null
}

export interface PanelMetricaConfiguracionWidgetRequestDto {
  tipoResultado: TipoResultado | null
  configuracionWidget: ConfiguracionWidgetDto | null
}

export interface WidgetMetadataDto {
  panelMetricaId: number
  metricaId: number
  codigo: string
  nombre: string
  tipoMetrica: string
  tipoVisualizacion: string
  tipoResultadoWidgetConfigurado: string | null
  tipoResultadoActual: string
  tipoResultadosPermitidos: string[]
  configuracionWidgetActual: ConfiguracionWidgetDto | null
}

export interface DashboardPanelMetadataResponseDto {
  panel: PanelClinicoResponseDto
  dataset: DatasetClinicoResponseDto
  camposFechaPermitidos: string[]
  camposAgrupacionPermitidos: string[]
  widgets: WidgetMetadataDto[]
}

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
  // --- Fase 6.9I.2 ---
  /** OK o SIN_BASE_EVALUABLE. Si no es OK, `valor` es null y hay que explicar por qué. */
  estado?: EstadoResultadoMetrica | null
  /** Resultado no numérico: fecha de un MINIMO/MAXIMO, etiqueta de CATEGORIA_PRINCIPAL. */
  valorTexto?: string | null
  etiquetaNumerador?: string | null
  etiquetaDenominador?: string | null
}

// --- Fase 6.9I.2: perfil analítico de las columnas ---

export interface OperacionDisponibleDto {
  codigo: TipoMetrica
  nombre: string
  explicacion: string
  sufijoCodigo: string
  planResultado: 'UNICO' | 'AGRUPADO' | 'SERIE'
  visualizacionRecomendada: string
  porcentual: boolean
  exigeTopN: boolean
  advertencia: string | null
}

export interface PerfilCampoDto {
  codigo: string
  etiqueta: string
  tipoDato: string
  activo: boolean
  rolSugerido: RolAnaliticoCampo
  rolesAlternativos: RolAnaliticoCampo[]
  totalRegistros: number
  valoresInformados: number
  valoresSinDato: number
  valoresDistintos: number
  cardinalidad: 'BAJA' | 'MEDIA' | 'ALTA'
  completitud: number | null
  valorMinimo: string | null
  valorMaximo: string | null
  valoresEjemplo: string[]
  /** Operaciones para el rol sugerido. Nunca vacío. */
  operaciones: OperacionDisponibleDto[]
  /** Operaciones de cada rol elegible (el sugerido incluido), resueltas por el backend. */
  operacionesPorRol: Record<string, OperacionDisponibleDto[]>
  esIdentificadorIndividuo: boolean
}

export interface PerfilCamposResponseDto {
  dataset: DatasetClinicoResponseDto
  totalRegistros: number
  campos: PerfilCampoDto[]
  campoIndividuo: string | null
  umbralCardinalidadCategorica: number
  maxCategoriasDonut: number
}

export interface PuntoSerieDto {
  estado?: EstadoResultadoMetrica | null
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
  estado?: EstadoResultadoMetrica | null
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

export interface DashboardPanelRequestDto {
  fechaDesde?: string | null
  fechaHasta?: string | null
  granularidad?: Granularidad | null
  campoFecha?: string | null
  filtros?: FiltroMetricaDto[] | null
}

export interface DashboardFiltrosAplicadosDto {
  fechaDesde: string | null
  fechaHasta: string | null
  granularidad: string | null
  filtros: FiltroMetricaDto[] | null
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

// --- Fase 6.9H.3: detalle del subconjunto seleccionado ---

export interface SubconjuntoRequestDto {
  /** Los MISMOS filtros que el dashboard cruzado: globales + selección gráfica. */
  filtros: FiltroMetricaDto[]
  campoIndividuo?: string | null
  pagina?: number | null
  tamano?: number | null
  ordenCampo?: string | null
  ordenDireccion?: 'ASC' | 'DESC' | null
  busquedaIndividuo?: string | null
}

export interface ColumnaSubconjuntoDto {
  codigo: string
  etiqueta: string
  tipoDato: string
  orden: number
  identificador: boolean
}

export interface FilaSubconjuntoDto {
  clave: string
  numeroRegistros: number | null
  valores: Record<string, unknown>
}

export interface SubconjuntoResumenDto {
  totalRegistros: number
  totalPacientesUnicos: number | null
  campoIndividuo: string | null
  etiquetaCampoIndividuo: string | null
  tienePacientes: boolean
  periodoDesde: string | null
  periodoHasta: string | null
}

export interface SubconjuntoPaginaDto {
  contenido: FilaSubconjuntoDto[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
  columnas: ColumnaSubconjuntoDto[]
}

export interface PerfilNumericoDto {
  campo: string
  etiqueta: string
  valoresValidos: number
  valoresAusentes: number
  media: number | null
  mediana: number | null
  minimo: number | null
  maximo: number | null
}

export interface CategoriaPerfilDto {
  /** Valor técnico sin traducir. */
  valor: string
  conteo: number
  porcentaje: number | null
}

export interface PerfilCategoricoDto {
  campo: string
  etiqueta: string
  tipoDato: string
  valoresValidos: number
  valoresAusentes: number
  categorias: CategoriaPerfilDto[]
  otrasCategorias: number
}

export interface PerfilFechaDto {
  campo: string
  etiqueta: string
  valoresValidos: number
  valoresAusentes: number
  primera: string | null
  ultima: string | null
}

export interface SubconjuntoPerfilDto {
  /** "REGISTROS": denominador explícito de todas las distribuciones. */
  baseCalculo: string
  totalRegistros: number
  totalPacientesUnicos: number | null
  registrosPorPaciente: number | null
  numericos: PerfilNumericoDto[]
  categoricos: PerfilCategoricoDto[]
  booleanos: PerfilCategoricoDto[]
  fechas: PerfilFechaDto[]
}

// --- Fase 6.8C: copia interna de trabajo (corrección de filas sin tocar el archivo original) ---

export interface ImportacionTrabajoResponseDto {
  id: number
  datasetId: number
  plantillaId: number
  nombreArchivoOriginal: string | null
  origen: string | null
  indiceHoja: number | null
  filaCabecera: number | null
  estado: string
  totalFilasLeidas: number
  totalFilasExcluidas: number
  totalErrores: number
  totalAdvertencias: number
  importable: boolean
  fechaCreacion: string
  fechaUltimaRevalidacion: string | null
}

export interface ErrorImportacionTrabajoDto {
  numeroFila: number | null
  nombreColumna: string | null
  valorOriginal: string | null
  tipoError: string
  severidad: string
  mensaje: string
}

export interface FilaImportacionTrabajoResponseDto {
  id: number
  numeroFilaOriginal: number
  excluida: boolean
  valoresOriginales: Record<string, string | null>
  valoresCorregidos: Record<string, string | null>
  errores: ErrorImportacionTrabajoDto[]
}

export interface PaginaFilasImportacionTrabajoResponseDto {
  content: FilaImportacionTrabajoResponseDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CrearImportacionTrabajoResponseDto {
  importacionTrabajo: ImportacionTrabajoResponseDto
  resumen: string
}

export interface RevalidarImportacionTrabajoResponseDto {
  importacionTrabajo: ImportacionTrabajoResponseDto
  resumen: string
}

export interface ImportarDesdeTrabajoResponseDto {
  importacionTrabajo: ImportacionTrabajoResponseDto
  importacionGenerica: ImportacionGenericaResponseDto
  filasImportadas: number
  filasExcluidas: number
  resumen: string
}

// --- Fase 6.8E.2: reanudación de datasets BORRADOR/VALIDANDO ---

export type PasoRecomendadoReanudacion =
  | 'SUBIR_ARCHIVO'
  | 'COLUMNAS'
  | 'CORREGIR_FILAS'
  | 'IMPORTAR'
  | 'RESULTADO'
  | 'DETALLE_DATASET'

export interface ReanudarBorradorDatasetDto {
  datasetId: number
  codigo: string
  nombre: string
  estadoDataset: string
  puedeReanudarse: boolean
  motivoNoReanudable: string | null
  pasoRecomendado: PasoRecomendadoReanudacion | string
  importacionTrabajoId: number | null
  plantillaId: number | null
  importacionGenericaId: number | null
  totalFilasLeidas: number | null
  totalErrores: number | null
  totalAdvertencias: number | null
  totalFilasExcluidas: number | null
  importable: boolean | null
  estadoImportacionTrabajo: string | null
  mensaje: string
  huboCopiaDescartada: boolean
}

// --- Fase 6.8E.2.1: reconstrucción de columnas al reanudar ---

export interface ColumnaReanudacionDto {
  nombreOriginal: string
  nombreVisible: string
  usar: boolean
  tipoDato: string | null
  campoClinicoCodigo: string | null
  campoClinicoEtiqueta: string | null
  obligatorio: boolean
  mapeada: boolean
}

export interface ColumnasReanudacionResponseDto {
  importacionTrabajoId: number
  datasetId: number
  plantillaId: number
  columnas: ColumnaReanudacionDto[]
  mensaje: string | null
}

// --- Fase 6.8D: trazabilidad de importaciones y correcciones ---

export type TipoEventoImportacionTrabajo =
  | 'COPIA_CREADA'
  | 'CELDA_CORREGIDA'
  | 'CORRECCION_CELDA_DESHECHA'
  | 'CORRECCIONES_FILA_DESHECHAS'
  | 'TODAS_CORRECCIONES_DESHECHAS'
  | 'FILA_EXCLUIDA'
  | 'FILA_INCLUIDA'
  | 'FILAS_SIMILARES_EXCLUIDAS'
  | 'TODAS_EXCLUSIONES_DESHECHAS'
  | 'COPIA_RESTAURADA_ORIGINAL'
  | 'COLUMNA_RELLENADA'
  | 'COLUMNA_NORMALIZADA'
  | 'REVALIDACION_EJECUTADA'
  | 'IMPORTACION_REALIZADA'
  | 'DATASET_ACTIVADO'
  | 'BORRADOR_DESCARTADO'
  | 'COPIA_DESCARTADA'

export interface EventoImportacionTrabajoDto {
  id: number
  tipoEvento: TipoEventoImportacionTrabajo | string
  numeroFilaOriginal: number | null
  nombreColumna: string | null
  valorAnterior: string | null
  valorNuevo: string | null
  detalle: string | null
  fechaEvento: string
  actor: string | null
}

export interface ResumenTrazabilidadImportacionTrabajoDto {
  importacionTrabajoId: number
  importacionGenericaId: number | null
  datasetId: number
  plantillaId: number
  nombreArchivoOriginal: string | null
  hashArchivoOriginal: string | null
  estado: string
  totalFilasLeidas: number
  totalFilasExcluidas: number
  totalErrores: number
  totalAdvertencias: number
  importable: boolean
  totalEventos: number
  totalCorreccionesManuales: number
  totalCorreccionesEnBloque: number
  totalNormalizaciones: number
  totalExclusiones: number
  totalRestauraciones: number
  fechaCreacion: string
  fechaUltimaRevalidacion: string | null
}

export interface TrazabilidadImportacionTrabajoResponseDto {
  resumen: ResumenTrazabilidadImportacionTrabajoDto
  eventos: EventoImportacionTrabajoDto[]
}

// --- Fase 6.9I.4: plantilla de dashboard clínico ILQ ---

export interface CompatibilidadDashboardIlqDto {
  datasetId: number
  datasetCodigo: string
  /** Están todos los campos esenciales y activos. */
  compatible: boolean
  /** Ya existe el panel: aplicar sería actualizar, no crear. */
  yaAplicado: boolean
  panelId: number | null
  camposEncontrados: string[]
  camposAusentes: string[]
  totalElementos: number
}

export interface AplicacionDashboardIlqDto {
  panelId: number
  panelCodigo: string
  panelNombre: string
  /** false = el panel ya existía y se ha actualizado. */
  panelCreado: boolean
  metricasCreadas: number
  metricasActualizadas: number
  widgetsCreados: number
  widgetsActualizados: number
  totalMetricas: number
  totalWidgets: number
}

// --- Fase 6.9I.4.1: dashboard recomendado ---

export interface PropuestaWidgetDto {
  codigoMetrica: string
  nombre: string
  descripcion: string | null
  tipoMetrica: TipoMetrica
  configuracion: ConfiguracionMetricaDto
  unidad: string | null
  decimales: number | null
  campoOrigen: string | null
  campoOrigenEtiqueta: string | null
  /** Por qué se propone: «Campo fundamental», «Campo obligatorio»… */
  motivo: string
  /** Puntuación con la que se ordenó; se expone para poder auditarla. */
  prioridad: number
  tipoVisualizacion: TipoVisualizacion
  ancho: number
  orden: number
  tipoResultado: TipoResultado
  configuracionWidget: ConfiguracionWidgetDto | null
  advertencia: string | null
}

export interface PropuestaDashboardResponseDto {
  datasetId: number
  datasetCodigo: string
  suficiente: boolean
  motivoInsuficiente: string | null
  propuestas: PropuestaWidgetDto[]
  maximoWidgets: number
  /** El dataset admite además la plantilla clínica de ILQ. */
  compatibleIlq: boolean
}
