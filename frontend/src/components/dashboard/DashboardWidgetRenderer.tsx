import { useEffect, useMemo, useRef, useState } from 'react'
import type { DashboardWidgetDto, FiltroMetricaDto, TipoVisualizacion } from '../../api/types'
import { clampAncho } from '../../utils/formatters'
import { WidgetError } from '../widgets/WidgetError'
import { BarChartWidget } from './BarChartWidget'
import type { DatoBarra, SeleccionChart } from './BarChartWidget'
import { LineChartWidget } from './LineChartWidget'
import type { SeleccionTemporalChart, SerieLinea } from './LineChartWidget'
import { PieChartWidget } from './PieChartWidget'
import { KpiWidget } from './KpiWidget'
import { TableWidget } from './TableWidget'
import { ChartEmptyState, ChartZeroState } from './ChartEmptyState'
import {
  ETIQUETA_TIPO_RESULTADO,
  normalizarTipoVisualizacion,
  visualizacionesCompatibles,
  visualizacionesUnicas,
} from './visualizacionesCompatibles'
import { capacidadesExploracionWidget } from './capacidadesExploracionWidget'
import type { ModoExploracion } from './capacidadesExploracionWidget'
import {
  ejecutarExploracionLocal,
  etiquetaExploracionActiva,
  resolverSubtituloWidget,
  widgetSinDatos,
  widgetTodoCero,
} from './exploracionWidget'
import { WidgetExploracionControls } from './WidgetExploracionControls'
import { WidgetMenu } from './WidgetMenu'
import type { OpcionMenuWidget } from './WidgetMenu'
import { resolverRangoTemporal, resolverSeleccionTemporalWidget, resolverSeleccionWidget } from './seleccionGrafica'
import type { SeleccionGrafica } from './seleccionGrafica'
import type { CampoFiltroCategoria, CampoIndividuo } from './camposFiltroDashboard'
import { formatearEtiquetaCategoria } from './camposFiltroDashboard'
import styles from './DashboardWidgetRenderer.module.css'

const MAX_SERIES_LINEA = 4

interface DashboardWidgetRendererProps {
  widget: DashboardWidgetDto
  /**
   * Acciones del menú «⋮» de la tarjeta (editar, agrupar, tamaño, quitar). Las
   * decide la página, que es quien conoce el panel y puede navegar o recargar.
   */
  accionesMenu?: OpcionMenuWidget[]
  /**
   * Ausente en contextos de solo lectura (no aplica aquí, pero deja la puerta
   * abierta). Cuando está presente, el widget ofrece el selector
   * "Visualización" para cambiar tipoVisualizacion; ese cambio SÍ se persiste.
   */
  onCambiarVisualizacion?: (panelMetricaId: number, nuevoTipo: TipoVisualizacion) => Promise<void>
  /** Campos por los que se puede agrupar localmente (ya filtrados: categóricos con valores reales). */
  camposAgrupables?: CampoFiltroCategoria[]
  /** Campo que identifica al individuo en este dataset, si existe. */
  campoIndividuo?: CampoIndividuo | null
  /** Filtros globales aplicados al dashboard: se combinan en AND con el local. */
  filtrosGlobales?: FiltroMetricaDto[]
  fechaDesde?: string | null
  fechaHasta?: string | null
  /** Config persistida del widget, necesaria para reejecutar una serie temporal. */
  campoFecha?: string | null
  campoSegmentacion?: string | null
  /** Agrupación guardada del widget: primera opción al entrar en modo Categoría. */
  campoAgrupacionPersistido?: string | null
  /**
   * Individuo ya fijado por el contexto global (si lo hay). Se calcula desde
   * los filtros APLICADOS, no desde el formulario sin aplicar.
   */
  individuoGlobal?: string | null

  // --- Cross-filtering (6.9H.1) ---
  /** Selección gráfica activa en todo el dashboard, si la hay. */
  seleccionGrafica?: SeleccionGrafica | null
  /** `configuracion.campoAgrupacion` de la métrica: única fuente para DISTRIBUCION. */
  campoAgrupacionMetrica?: string | null
  /** Campos FECHA reales del dataset: valida el campoFecha antes de usarlo. */
  camposFechaPermitidos?: string[]
  /** Nombre legible del campo de fecha, para el chip ("Fecha de cirugía"). */
  etiquetaCampoFecha?: string | null
  onSeleccionar?: (seleccion: SeleccionGrafica) => void
  /** Aviso de que la semántica del origen cambió y la selección ya no vale. */
  onSeleccionInvalidada?: (widgetOrigenId: number) => void
}

/**
 * Categorías (etiqueta/valor) disponibles en el widget, vengan de donde vengan.
 * La etiqueta se formatea aquí (true→Sí) para que gráficas, tooltips, títulos
 * SVG y aria-label reciban ya el texto legible; `valorOriginal` conserva el
 * valor técnico del backend, que es el que necesitarán los filtros y la
 * selección gráfica de 6.9H.
 */
function categorias(widget: DashboardWidgetDto): DatoBarra[] | null {
  const aDato = (i: { etiqueta: string; valor: number | null }): DatoBarra => ({
    etiqueta: formatearEtiquetaCategoria(i.etiqueta),
    valor: i.valor,
    valorOriginal: i.etiqueta,
  })

  if (widget.comparativa) return widget.comparativa.items.map(aDato)
  if (widget.resultadoActual?.items) return widget.resultadoActual.items.map(aDato)
  return null
}

/** Series temporales normalizadas: la simple es una serie con el título del widget. */
function seriesTemporales(widget: DashboardWidgetDto): SerieLinea[] | null {
  const serie = widget.serieTemporal
  if (!serie) return null
  // fechaInicio/fechaFin se arrastran tal cual: son el rango real del periodo
  // calculado por el backend, y evitan recalcularlo (y equivocarse) aquí.
  const aPunto = (p: { periodo: string; valor: number | null; fechaInicio: string; fechaFin: string }) => ({
    periodo: p.periodo,
    valor: p.valor,
    fechaInicio: p.fechaInicio,
    fechaFin: p.fechaFin,
  })

  if (serie.series) {
    return serie.series.map((s) => ({
      etiqueta: formatearEtiquetaCategoria(s.etiqueta),
      valorOriginal: s.etiqueta,
      puntos: s.puntos.map(aPunto),
    }))
  }
  if (serie.puntos) {
    return [{ etiqueta: widget.titulo, puntos: serie.puntos.map(aPunto) }]
  }
  return null
}

/**
 * Si este widget se va a pintar como un número grande. En ese caso los
 * controles van DEBAJO del valor y en versión compacta: el dato manda, y una
 * barra de exploración a media altura convertía la tarjeta en un formulario.
 */
function seRenderizaComoKpi(widget: DashboardWidgetDto): boolean {
  const actual = widget.resultadoActual
  const tieneValorSimple = Boolean(actual && actual.valor !== null && actual.valor !== undefined && !actual.items)
  return tieneValorSimple && (widget.tipoVisualizacion === 'KPI' || widget.tipoVisualizacion === 'TARJETA')
}

function cuerpoWidget(
  widget: DashboardWidgetDto,
  seleccion?: SeleccionChart,
  seleccionTemporal?: SeleccionTemporalChart,
) {
  if (widget.estado === 'ERROR') {
    return <WidgetError widget={widget} />
  }

  // Colección con estructura pero todos los valores a 0: en una gráfica no hay
  // nada que dibujar (barras de altura cero, línea sobre el eje) y parecería
  // rota. En TABLA sí se muestran las filas, para que el usuario vea
  // explícitamente cada categoría y su 0.
  if (widget.tipoVisualizacion !== 'TABLA' && widgetTodoCero(widget)) {
    return <ChartZeroState esSerie={Boolean(widget.serieTemporal)} />
  }

  const cats = categorias(widget)
  const series = seriesTemporales(widget)
  const actual = widget.resultadoActual
  const tieneValorSimple = actual && actual.valor !== null && actual.valor !== undefined && !actual.items

  switch (widget.tipoVisualizacion) {
    case 'KPI':
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
      return <TableWidget widget={widget} seleccion={seleccion} />

    case 'TARJETA':
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
      return <TableWidget widget={widget} seleccion={seleccion} />

    case 'TABLA':
      if (tieneValorSimple) return <KpiWidget resultado={actual} compacto />
      return <TableWidget widget={widget} seleccion={seleccion} />

    case 'BARRAS':
      if (cats) return <BarChartWidget datos={cats} seleccion={seleccion} />
      if (series) {
        // Una serie temporal en barras se representa por periodo; con varios
        // segmentos las barras se solaparían, así que cae a tabla.
        if (series.length === 1) {
          // Barras TEMPORALES: cada barra es un periodo, no una categoría. Se
          // reutiliza BarChartWidget, pero `valorOriginal` lleva el periodo
          // técnico y el handler crea una selección temporal, no categórica.
          return (
            <BarChartWidget
              datos={series[0].puntos.map((p) => ({
                etiqueta: seleccionTemporal?.resolverPeriodo(p.periodo)?.etiquetaVisible ?? p.periodo,
                valor: p.valor,
                valorOriginal: seleccionTemporal?.resolverPeriodo(p.periodo) ? p.periodo : undefined,
              }))}
              seleccion={
                seleccionTemporal && {
                  etiquetaCampo: seleccionTemporal.etiquetaCampo,
                  valorSeleccionado: seleccionTemporal.periodoSeleccionado,
                  onSeleccionar: (periodo) => seleccionTemporal.onSeleccionarPeriodo(periodo),
                }
              }
            />
          )
        }
        return <TableWidget widget={widget} nota="incompatible" />
      }
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
      return <ChartEmptyState motivo="sin-datos" />

    case 'LINEAS':
      if (series) {
        if (series.length > MAX_SERIES_LINEA) {
          return <TableWidget widget={widget} nota="incompatible" />
        }
        return (
          <LineChartWidget
            series={series}
            mostrarLeyenda={series.length > 1}
            seleccionTemporal={seleccionTemporal}
          />
        )
      }
      return <TableWidget widget={widget} nota="incompatible" />

    case 'PIE':
    case 'DONUT':
      if (cats) return <PieChartWidget datos={cats} seleccion={seleccion} />
      if (series) return <TableWidget widget={widget} nota="incompatible" />
      if (tieneValorSimple) return <KpiWidget resultado={actual} />
      return <ChartEmptyState motivo="sin-datos" />

    default:
      return <TableWidget widget={widget} seleccion={seleccion} />
  }
}

export function DashboardWidgetRenderer({
  widget,
  accionesMenu,
  onCambiarVisualizacion,
  camposAgrupables = [],
  campoIndividuo = null,
  filtrosGlobales = [],
  fechaDesde = null,
  fechaHasta = null,
  campoFecha = null,
  campoSegmentacion = null,
  campoAgrupacionPersistido = null,
  individuoGlobal = null,
  seleccionGrafica = null,
  campoAgrupacionMetrica = null,
  camposFechaPermitidos = [],
  etiquetaCampoFecha = null,
  onSeleccionar,
  onSeleccionInvalidada,
}: DashboardWidgetRendererProps) {
  const [guardando, setGuardando] = useState(false)
  const [errorVista, setErrorVista] = useState<string | null>(null)

  // Estado de exploración LOCAL: vive solo en memoria, nunca se persiste ni se
  // propaga a otros widgets. Al recargar la página desaparece y el widget
  // vuelve a su configuración guardada.
  const [modo, setModo] = useState<ModoExploracion>('ACTUAL')
  const [campoAgrupacion, setCampoAgrupacion] = useState('')
  const [valorIndividuo, setValorIndividuo] = useState('')
  const [resultadoLocal, setResultadoLocal] = useState<DashboardWidgetDto | null>(null)
  const [cargandoLocal, setCargandoLocal] = useState(false)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)
  /** Contador que solo sirve para volver a disparar el efecto al pulsar "Reintentar". */
  const [reintento, setReintento] = useState(0)

  const capacidades = capacidadesExploracionWidget(widget, camposAgrupables, campoIndividuo !== null)

  // El widget que se pinta: el explorado si hay exploración activa, si no el
  // que vino del dashboard con la configuración persistida.
  const mostrado = resultadoLocal ?? widget
  const conError = mostrado.estado === 'ERROR'

  const restablecer = () => {
    setModo('ACTUAL')
    setCampoAgrupacion('')
    setValorIndividuo('')
    setResultadoLocal(null)
    setErrorLocal(null)
  }

  /**
   * Campo con el que arranca el modo Categoría. Nunca se elige "el primero del
   * dataset": un desglose arbitrario por un campo sin relación con la métrica
   * sería una afirmación clínica que el usuario no ha pedido. Orden:
   * 1) la agrupación guardada del widget, 2) la que está usando el resultado
   * actual, 3) ninguna → estado guiado.
   */
  const resolverAgrupacionInicial = (): string => {
    const esUsable = (codigo: string | null | undefined) =>
      Boolean(codigo) && camposAgrupables.some((c) => c.codigo === codigo)

    if (esUsable(campoAgrupacionPersistido)) return campoAgrupacionPersistido as string
    if (esUsable(widget.comparativa?.agrupadoPor)) return widget.comparativa!.agrupadoPor
    return ''
  }

  /** Cambio de modo: descarta siempre el resultado anterior, que ya no corresponde. */
  const cambiarModo = (nuevo: ModoExploracion) => {
    setErrorLocal(null)
    setResultadoLocal(null)

    if (nuevo === 'ACTUAL') {
      restablecer()
      return
    }
    if (nuevo === 'CATEGORIA') {
      setValorIndividuo('')
      setCampoAgrupacion(resolverAgrupacionInicial())
    } else {
      setCampoAgrupacion('')
      setValorIndividuo('')
    }
    setModo(nuevo)
  }

  // Una exploración incompleta (modo elegido pero sin campo/individuo) no
  // dispara ninguna petición: se queda esperando a que el usuario complete.
  const exploracionCompleta =
    (modo === 'CATEGORIA' && Boolean(campoAgrupacion)) || (modo === 'INDIVIDUO' && Boolean(valorIndividuo))

  const filtrosGlobalesKey = JSON.stringify(filtrosGlobales)
  const peticionRef = useRef(0)

  // Si el contexto global pasa a estar limitado a un individuo mientras este
  // widget exploraba por individuo, el modo local deja de tener sentido (sería
  // "H003 AND H002", siempre vacío) y se vuelve a la vista guardada. Al
  // limpiar el paciente global, el modo vuelve a ofrecerse sin recargar.
  useEffect(() => {
    if (individuoGlobal && modo === 'INDIVIDUO') {
      restablecer()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [individuoGlobal])

  useEffect(() => {
    if (!exploracionCompleta) {
      setResultadoLocal(null)
      setErrorLocal(null)
      return
    }

    const controller = new AbortController()
    const idPeticion = ++peticionRef.current
    setCargandoLocal(true)
    setErrorLocal(null)

    ejecutarExploracionLocal(
      {
        widget,
        modo,
        campoAgrupacion,
        campoIndividuo: campoIndividuo?.codigo ?? null,
        valorIndividuo,
        filtrosGlobales,
        fechaDesde,
        fechaHasta,
        campoFecha,
        campoSegmentacion,
      },
      controller.signal,
    )
      .then((resultado) => {
        // Descarta respuestas de peticiones ya superadas por otra más reciente.
        if (idPeticion === peticionRef.current) setResultadoLocal(resultado)
      })
      .catch(() => {
        if (idPeticion === peticionRef.current && !controller.signal.aborted) {
          setErrorLocal('No se pudo actualizar esta vista.')
          setResultadoLocal(null)
        }
      })
      .finally(() => {
        if (idPeticion === peticionRef.current) setCargandoLocal(false)
      })

    return () => controller.abort()
    // Los filtros globales entran como clave serializada: si el usuario cambia
    // el filtro global, la vista local se recalcula manteniendo el AND.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exploracionCompleta, modo, campoAgrupacion, valorIndividuo, filtrosGlobalesKey, widget, reintento])

  // Fuente ÚNICA de la lista de visualizaciones: la misma que decide si se
  // renderiza el selector y la que llena sus <option>. Antes se normalizaba
  // dentro del componente hijo, de modo que había dos listas conceptuales.
  const visualizacionesRenderizables = onCambiarVisualizacion
    ? visualizacionesUnicas(visualizacionesCompatibles(mostrado))
    : []
  const ocupado = guardando || cargandoLocal

  const alCambiarVista = async (valor: string) => {
    if (!onCambiarVisualizacion) return
    setGuardando(true)
    setErrorVista(null)
    try {
      await onCambiarVisualizacion(widget.panelMetricaId, valor as TipoVisualizacion)
    } catch (err) {
      setErrorVista(err instanceof Error ? err.message : 'No se pudo cambiar la visualización.')
    } finally {
      setGuardando(false)
    }
  }

  const etiquetaExploracion = etiquetaExploracionActiva(
    modo,
    campoAgrupacion,
    valorIndividuo,
    camposAgrupables,
    campoIndividuo,
  )
  // Campo por el que agrupa el widget de forma persistida, en legible: sale de
  // lo que devolvió el backend (`agrupadoPor`) o de la config guardada, y se
  // traduce con las etiquetas de los campos del dataset.
  const codigoAgrupacionMostrada = mostrado.comparativa?.agrupadoPor ?? campoAgrupacionPersistido
  const etiquetaAgrupacionPersistida = codigoAgrupacionMostrada
    ? (camposAgrupables.find((c) => c.codigo === codigoAgrupacionMostrada)?.etiqueta ??
      codigoAgrupacionMostrada)
    : null

  const subtitulo = resolverSubtituloWidget(
    modo,
    mostrado.tipoResultado,
    etiquetaExploracion,
    ETIQUETA_TIPO_RESULTADO,
    etiquetaAgrupacionPersistida,
  )
  const sinDatosLocal = resultadoLocal !== null && !cargandoLocal && widgetSinDatos(resultadoLocal)
  const esKpi = seRenderizaComoKpi(mostrado)

  // --- Cross-filtering ---
  const capacidadSeleccion = resolverSeleccionWidget(
    mostrado,
    modo,
    campoAgrupacion,
    campoAgrupacionMetrica,
    camposAgrupables,
  )
  const esOrigen = seleccionGrafica?.widgetOrigenId === widget.panelMetricaId

  // Si el origen deja de poder sostener la selección (cambió el campo, pasó a
  // modo Paciente, dejó de estar desglosado…), se avisa al estado central para
  // que no quede un chip que ya no corresponde a lo que muestra el widget.
  const campoSeleccionable = capacidadSeleccion.campo
  useEffect(() => {
    if (!esOrigen || !onSeleccionInvalidada || !seleccionGrafica) return
    if (seleccionGrafica.tipo !== 'CATEGORIA') return
    if (!capacidadSeleccion.seleccionable || campoSeleccionable !== seleccionGrafica.campo) {
      onSeleccionInvalidada(widget.panelMetricaId)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [esOrigen, capacidadSeleccion.seleccionable, campoSeleccionable])

  // Capacidad TEMPORAL: separada de la categórica porque un periodo no es un
  // valor, es un rango. campoFecha viene de la config del widget (o del default
  // del backend, validado); la granularidad, de la respuesta.
  const capacidadTemporal = resolverSeleccionTemporalWidget(mostrado, modo, campoFecha, camposFechaPermitidos)

  /** Índice periodo → rango real que envió el backend. Nunca se recalcula. */
  const rangosPorPeriodo = useMemo(() => {
    const mapa = new Map<string, { fechaInicio: string; fechaFin: string }>()
    const serie = mostrado.serieTemporal
    if (!serie) return mapa
    const puntos = serie.puntos ?? serie.series?.flatMap((s) => s.puntos) ?? []
    for (const p of puntos) {
      if (p.fechaInicio && p.fechaFin) mapa.set(p.periodo, { fechaInicio: p.fechaInicio, fechaFin: p.fechaFin })
    }
    return mapa
  }, [mostrado])

  const resolverPeriodo = (periodo: string) => {
    if (!capacidadTemporal.seleccionable || !capacidadTemporal.granularidad) return null
    const rango = rangosPorPeriodo.get(periodo)
    return resolverRangoTemporal(periodo, capacidadTemporal.granularidad, rango?.fechaInicio, rango?.fechaFin)
  }

  // La selección TEMPORAL deja de valer si cambia el campo de fecha, la
  // granularidad, o el periodo desaparece de la serie. Cambiar solo entre
  // Línea y Barras no la invalida: campoFecha, granularidad y periodo siguen
  // siendo los mismos.
  const claveTemporal = `${capacidadTemporal.seleccionable}|${capacidadTemporal.campoFecha}|${capacidadTemporal.granularidad}`
  const periodoActivo = seleccionGrafica?.tipo === 'TEMPORAL' ? seleccionGrafica.periodoOriginal : null
  const periodoSigueExistiendo = periodoActivo !== null && rangosPorPeriodo.has(periodoActivo)

  useEffect(() => {
    if (!esOrigen || !onSeleccionInvalidada || seleccionGrafica?.tipo !== 'TEMPORAL') return
    const mismoCampoYGranularidad =
      capacidadTemporal.seleccionable &&
      capacidadTemporal.campoFecha === seleccionGrafica.campoFecha &&
      capacidadTemporal.granularidad === seleccionGrafica.granularidad

    if (!mismoCampoYGranularidad || !periodoSigueExistiendo) {
      onSeleccionInvalidada(widget.panelMetricaId)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [esOrigen, claveTemporal, periodoSigueExistiendo])

  const seleccionTemporalChart: SeleccionTemporalChart | undefined =
    capacidadTemporal.seleccionable && capacidadTemporal.campoFecha && onSeleccionar
      ? {
          etiquetaCampo: etiquetaCampoFecha ?? capacidadTemporal.campoFecha,
          periodoSeleccionado:
            esOrigen && seleccionGrafica?.tipo === 'TEMPORAL' ? seleccionGrafica.periodoOriginal : null,
          resolverPeriodo: (periodo) => {
            const r = resolverPeriodo(periodo)
            return r ? { etiquetaVisible: r.etiquetaVisible } : null
          },
          onSeleccionarPeriodo: (periodo) => {
            const rango = resolverPeriodo(periodo)
            // Periodo no interpretable → no se construye filtro ni petición.
            if (!rango) return
            onSeleccionar({
              tipo: 'TEMPORAL',
              widgetOrigenId: widget.panelMetricaId,
              campoFecha: capacidadTemporal.campoFecha!,
              granularidad: capacidadTemporal.granularidad!,
              periodoOriginal: periodo,
              fechaDesde: rango.fechaDesde,
              fechaHasta: rango.fechaHasta,
              etiquetaVisible: rango.etiquetaVisible,
              etiquetaCampo: etiquetaCampoFecha ?? capacidadTemporal.campoFecha!,
            })
          },
        }
      : undefined

  const seleccionChart: SeleccionChart | undefined =
    capacidadSeleccion.seleccionable && capacidadSeleccion.campo && onSeleccionar
      ? {
          etiquetaCampo: capacidadSeleccion.etiquetaCampo ?? capacidadSeleccion.campo,
          // Solo el widget de ORIGEN resalta/atenúa: los demás muestran su
          // resultado ya filtrado, sin marcar nada.
          valorSeleccionado:
            esOrigen && seleccionGrafica?.tipo === 'CATEGORIA' ? seleccionGrafica.valorOriginal : null,
          onSeleccionar: (valorOriginal, etiquetaVisible) =>
            onSeleccionar({
              tipo: 'CATEGORIA',
              widgetOrigenId: widget.panelMetricaId,
              campo: capacidadSeleccion.campo!,
              operador: 'EQ',
              valorOriginal,
              etiquetaVisible,
              etiquetaCampo: capacidadSeleccion.etiquetaCampo ?? capacidadSeleccion.campo!,
            }),
        }
      : undefined

  // Un KPI desglosado deja de ser un número y pasa a ser una comparativa: en
  // una columna estrecha crecería a lo alto y rompería la fila. Mientras dura
  // la exploración ocupa el ancho completo de la rejilla; al volver a Actual o
  // Paciente recupera su tamaño. El `ancho` guardado en PanelMetrica no se
  // toca: es puramente visual y temporal.
  const expandido = seRenderizaComoKpi(widget) && modo === 'CATEGORIA' && resultadoLocal !== null

  const controles = (
    <WidgetExploracionControls
      capacidades={capacidades}
      modo={modo}
      campoAgrupacion={campoAgrupacion}
      valorIndividuo={valorIndividuo}
      campoIndividuo={campoIndividuo}
      opcionesVisualizacion={visualizacionesRenderizables}
      visualizacionActual={normalizarTipoVisualizacion(mostrado.tipoVisualizacion)}
      ocupado={ocupado}
      compacto={seRenderizaComoKpi(widget)}
      individuoGlobal={individuoGlobal}
      onModo={cambiarModo}
      onCampoAgrupacion={setCampoAgrupacion}
      onValorIndividuo={setValorIndividuo}
      onVisualizacion={alCambiarVista}
    />
  )

  // Modo elegido pero sin completar: NUNCA se deja debajo el resultado
  // anterior, porque el usuario lo leería como si correspondiera a la
  // configuración local a medio hacer.
  const guiado =
    modo === 'CATEGORIA' && !campoAgrupacion
      ? {
          titulo: 'Selecciona un campo para desglosar este indicador.',
          texto: 'El resultado se actualizará únicamente en esta tarjeta.',
        }
      : modo === 'INDIVIDUO' && !valorIndividuo
        ? {
            titulo: `Selecciona un ${campoIndividuo ? 'paciente' : 'individuo'} para estudiar este indicador.`,
            texto: 'El resto del dashboard no se modificará.',
          }
        : null

  const cuerpo = (
    <div className={`${styles.cardBody} ${esKpi ? styles.cardBodyKpi : ''}`}>
      {guiado ? (
        <div className={styles.bloqueGuiado}>
          <p className={styles.bloqueGuiadoTitulo}>{guiado.titulo}</p>
          <p className={styles.bloqueLocalTexto}>{guiado.texto}</p>
        </div>
      ) : cargandoLocal ? (
        <p className={styles.cargandoLocal}>Actualizando esta vista…</p>
      ) : errorLocal ? (
        <div className={styles.bloqueLocal} role="alert">
          <p className={styles.bloqueLocalTitulo}>{errorLocal}</p>
          <div className={styles.bloqueLocalAcciones}>
            <button
              type="button"
              className="btn btnSecondary"
              onClick={() => {
                setErrorLocal(null)
                setReintento((r) => r + 1)
              }}
            >
              Reintentar
            </button>
            <button type="button" className="btn btnSecondary" onClick={restablecer}>
              Restablecer vista
            </button>
          </div>
        </div>
      ) : sinDatosLocal ? (
        <div className={styles.bloqueLocal}>
          <p className={styles.bloqueLocalTitulo}>No hay datos para esta vista.</p>
          <p className={styles.bloqueLocalTexto}>Prueba con otra categoría, otro individuo o restablece la vista.</p>
          <button type="button" className="btn btnSecondary" onClick={restablecer}>
            Restablecer vista
          </button>
        </div>
      ) : (
        cuerpoWidget(mostrado, seleccionChart, seleccionTemporalChart)
      )}
    </div>
  )

  return (
    <div
      className={`${styles.widget} ${expandido ? styles.widgetExploracionExpandida : ''}`}
      style={{ ['--span' as string]: clampAncho(widget.ancho) }}
    >
      <div className={`${styles.card} ${conError ? styles.cardError : ''} ${esOrigen ? styles.cardOrigen : ''}`}>
        <div className={styles.cardHeader}>
          <div className={styles.cardTituloFila}>
            <h3 className={styles.cardTitle}>{widget.titulo}</h3>
            {/* Acciones del widget recogidas tras «⋮»: cuatro botones fijos en
                cada tarjeta competirían con el propio dato. */}
            {accionesMenu && accionesMenu.length > 0 && (
              <WidgetMenu titulo={widget.titulo} opciones={accionesMenu} />
            )}
          </div>
          {/* Una sola frase de contexto: durante la exploración manda esta;
              en Actual, el tipo de resultado persistido. */}
          <p className={styles.cardMeta}>
            <span className={modo !== 'ACTUAL' ? styles.cardContexto : undefined}>{subtitulo}</span>
            {widget.descripcion && <span className={styles.cardDescripcion}> · {widget.descripcion}</span>}
          </p>
        </div>

        {/* El valor persistido solo va antes de los controles cuando el widget
            es un KPI en modo Actual. En cuanto hay exploración, los controles
            preceden al resultado: si no, el estado guiado ("selecciona un
            campo…") aparecía ANTES del selector que sirve para resolverlo. */}
        {esKpi && modo === 'ACTUAL' ? (
          <>
            {cuerpo}
            {controles}
          </>
        ) : (
          <>
            {controles}
            {cuerpo}
          </>
        )}

        {errorVista && (
          <p className={styles.errorVista} role="alert">
            {errorVista}
          </p>
        )}

        {/* Pie: no repite la frase de la cabecera ("Desglosado por Sexo"),
            solo recuerda el ámbito y ofrece la vuelta atrás. */}
        {etiquetaExploracion && !errorLocal && (
          <div className={styles.vistaLocal}>
            <span className={styles.vistaLocalTexto}>Vista local · {etiquetaExploracion}</span>
            <button type="button" className={styles.restablecer} disabled={ocupado} onClick={restablecer}>
              Restablecer
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
