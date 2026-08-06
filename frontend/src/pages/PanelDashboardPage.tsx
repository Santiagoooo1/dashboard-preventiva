import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import type {
  CatalogoFrontendResponseDto,
  MetricaClinicaResponseDto,
  PanelMetricaConfiguracionWidgetRequestDto,
  PanelMetricaRequestDto,
  ConfiguracionWidgetDto,
  DashboardPanelResponseDto,
  DashboardWidgetDto,
  SubconjuntoResumenDto,
  PanelMetricaResponseDto,
  TipoVisualizacion,
  WidgetMetadataDto,
} from '../api/types'
import { ejecutarDashboard } from '../api/dashboardApi'
import {
  actualizarConfiguracionWidget,
  actualizarWidget,
  anadirWidget as anadirWidgetApi,
  listarWidgets,
  obtenerDashboardMetadata,
  quitarWidget,
} from '../api/panelesApi'
import { getCatalogo } from '../api/frontendCatalogApi'
import { listarValoresUnicosDeCampo, obtenerFrontendMetadata } from '../api/datasetApi'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { DashboardFilters, FILTROS_VACIOS, aRequest } from '../components/dashboard/DashboardFilters'
import type { ValoresFiltros } from '../components/dashboard/DashboardFilters'
import { clasificarCampos, detectarCampoIndividuo } from '../components/dashboard/camposFiltroDashboard'
import type {
  CampoFiltroCategoria,
  CampoIndividuo,
  CamposClasificados,
} from '../components/dashboard/camposFiltroDashboard'
import { widgetSinDatos, widgetTodoCero } from '../components/dashboard/exploracionWidget'
import {
  CAMPO_FECHA_POR_DEFECTO,
  alternarSeleccion,
  combinarFiltros,
  filtrosDelSubconjunto,
  firmaFiltros,
  validarIntegridadCruce,
} from '../components/dashboard/seleccionGrafica'
import { SubconjuntoPanel } from '../components/dashboard/SubconjuntoPanel'
import { resumenSubconjunto } from '../api/subconjuntoApi'
import { formatearEtiquetaCategoria } from '../components/dashboard/camposFiltroDashboard'
import type { SeleccionGrafica } from '../components/dashboard/seleccionGrafica'
import { SeleccionGraficaPanel } from '../components/dashboard/SeleccionGraficaPanel'
import { listarMetricas } from '../api/metricasApi'
import { DashboardWidgetRenderer } from '../components/dashboard/DashboardWidgetRenderer'
import type { OpcionMenuWidget } from '../components/dashboard/WidgetMenu'
import { WidgetForm } from '../components/paneles/WidgetForm'
import { WidgetEditarForm } from '../components/paneles/WidgetEditarForm'
import { DashboardHeader } from '../components/dashboard/DashboardHeader'
import styles from './PanelDashboardPage.module.css'

const CAMPOS_FECHA_O_NUMERICOS_EXCLUIDOS = new Set(['FECHA', 'ENTERO', 'DECIMAL'])
const SIN_CAMPOS: CamposClasificados = { principales: [], avanzados: [] }

/**
 * Condición del subconjunto en lenguaje de usuario. Nunca muestra operadores
 * técnicos (GTE/LTE) ni valores sin traducir.
 */
function descripcionSeleccion(seleccion: SeleccionGrafica): string {
  return seleccion.tipo === 'TEMPORAL'
    ? `Periodo: ${seleccion.etiquetaVisible}`
    : `${seleccion.etiquetaCampo}: ${seleccion.etiquetaVisible}`
}

function hayFiltrosActivos(valores: ValoresFiltros): boolean {
  return (
    Boolean(valores.paciente.trim()) || Object.values(valores.camposCategoria).some((v) => Boolean(v))
  )
}

export function PanelDashboardPage() {
  const { panelId } = useParams<{ panelId: string }>()
  const [searchParams] = useSearchParams()
  const esDashboardInicial = searchParams.get('inicial') === '1'

  const [datos, setDatos] = useState<DashboardPanelResponseDto | null>(null)
  const [cargandoInicial, setCargandoInicial] = useState(true)
  const [aplicando, setAplicando] = useState(false)
  const [errorCarga, setErrorCarga] = useState<string | null>(null)

  const [catalogo, setCatalogo] = useState<CatalogoFrontendResponseDto | null>(null)
  const [camposFecha, setCamposFecha] = useState<string[] | null>(null)
  const [errorMetadata, setErrorMetadata] = useState<string | null>(null)

  const [tienePaciente, setTienePaciente] = useState(false)
  const [valoresPaciente, setValoresPaciente] = useState<string[]>([])
  const [campos, setCampos] = useState<CamposClasificados>(SIN_CAMPOS)
  const [errorCamposCategoria, setErrorCamposCategoria] = useState<string | null>(null)
  /** Campo que identifica al individuo (paciente/HC) en este dataset, si existe. */
  const [campoIndividuo, setCampoIndividuo] = useState<CampoIndividuo | null>(null)
  /** código → etiqueta de TODOS los campos, incluidos los de tipo FECHA. */
  const [etiquetasCampos, setEtiquetasCampos] = useState<Record<string, string>>({})
  /** Config persistida de cada widget, necesaria para reejecutar series temporales. */
  const [configPorWidget, setConfigPorWidget] = useState<Record<number, ConfiguracionWidgetDto | null>>({})
  // Qué formas de resultado admite cada widget, según el backend. Es lo que
  // decide si «Agrupar o segmentar» está disponible en su menú.
  const [metadataWidgets, setMetadataWidgets] = useState<Record<number, string[]>>({})
  // Metadata completa y widgets persistidos: hacen falta para poder EDITAR sin
  // salir del dashboard.
  const [metaCompleta, setMetaCompleta] = useState<Record<number, WidgetMetadataDto>>({})
  const [widgetEnEdicion, setWidgetEnEdicion] = useState<{ id: number; enfocarAgrupacion: boolean } | null>(
    null,
  )

  // `filtros` es lo que el usuario está editando en el formulario;
  // `filtrosAplicados` es lo que realmente se envió al backend y por tanto lo
  // que describe el dashboard que se está viendo. Separarlos evita presentar
  // como "activo" un valor que el usuario aún no ha aplicado.
  const [filtros, setFiltros] = useState<ValoresFiltros>(FILTROS_VACIOS)
  const [filtrosAplicados, setFiltrosAplicados] = useState<ValoresFiltros>(FILTROS_VACIOS)

  // --- Cross-filtering (nivel 3) ---
  // Una única selección activa en todo el dashboard. `datos` es el resultado
  // BASE (solo filtros globales) y `datosCruzados` el resultado con la
  // selección aplicada: el widget de origen sigue pintándose con el base para
  // conservar todas sus categorías, y el resto usa el cruzado.
  const [seleccionGrafica, setSeleccionGrafica] = useState<SeleccionGrafica | null>(null)
  const [datosCruzados, setDatosCruzados] = useState<DashboardPanelResponseDto | null>(null)
  const [cruzando, setCruzando] = useState(false)
  const [errorCruce, setErrorCruce] = useState<string | null>(null)
  const [reintentoCruce, setReintentoCruce] = useState(0)
  /** Descarta respuestas de selecciones ya superadas por otra más reciente. */
  const peticionCruceRef = useRef(0)
  /** `configuracion.campoAgrupacion` por métrica: única fuente para DISTRIBUCION. */
  const [campoAgrupacionPorMetrica, setCampoAgrupacionPorMetrica] = useState<Record<number, string | null>>({})

  // --- Detalle del subconjunto (6.9H.3) ---
  // Estado propio: un fallo aquí no puede tumbar el dashboard ni la selección.
  const [detalleAbierto, setDetalleAbierto] = useState(false)

  // --- Gestión de widgets desde el propio dashboard (Fase 6.9I.4.1) ---
  const [formWidgetAbierto, setFormWidgetAbierto] = useState(false)
  const [guardandoWidget, setGuardandoWidget] = useState(false)
  const [errorWidgets, setErrorWidgets] = useState<string | null>(null)
  const [mensajeWidgets, setMensajeWidgets] = useState<string | null>(null)
  const [catalogoWidgets, setCatalogoWidgets] = useState<{
    metricas: MetricaClinicaResponseDto[]
    catalogo: CatalogoFrontendResponseDto
  } | null>(null)
  const [resumenSub, setResumenSub] = useState<SubconjuntoResumenDto | null>(null)

  // Definición "en crudo" de cada widget (metricaId, título/descripción
  // personalizados, orden, ancho): la necesitamos completa para poder hacer
  // PUT al cambiar solo tipoVisualizacion, porque el backend espera el objeto
  // entero (ver PanelMetricaServiceImpl.actualizar) y DashboardWidgetDto solo
  // trae los valores ya resueltos para pintar, no los campos en crudo.
  const [widgetsRaw, setWidgetsRaw] = useState<PanelMetricaResponseDto[]>([])

  const cargarDashboard = useCallback(
    async (valores: ValoresFiltros, inicial: boolean) => {
      if (inicial) setCargandoInicial(true)
      else setAplicando(true)
      setErrorCarga(null)
      try {
        setDatos(await ejecutarDashboard(panelId ?? '', aRequest(valores)))
        setFiltrosAplicados(valores)
      } catch (err) {
        setErrorCarga(err instanceof Error ? err.message : 'Error al cargar el dashboard.')
        if (inicial) setDatos(null)
      } finally {
        setCargandoInicial(false)
        setAplicando(false)
      }
    },
    [panelId],
  )

  useEffect(() => {
    cargarDashboard(FILTROS_VACIOS, true)
  }, [cargarDashboard])

  // Catálogo de métricas del dataset, para poder añadir un widget sin salir del
  // dashboard. Se carga en cuanto se conoce el dataset y es auxiliar: si falla,
  // el dashboard se ve igual y la barra remite a "Organizar".
  useEffect(() => {
    if (!datos?.dataset.id || catalogoWidgets) return
    const controller = new AbortController()
    Promise.all([
      listarMetricas(datos.dataset.id, controller.signal),
      getCatalogo(controller.signal),
    ])
      .then(([metricas, cat]) => setCatalogoWidgets({ metricas, catalogo: cat }))
      .catch(() => {
        if (!controller.signal.aborted) setCatalogoWidgets(null)
      })
    return () => controller.abort()
  }, [datos?.dataset.id, catalogoWidgets])

  /** Añade un widget y recarga el dashboard sin abandonar la pantalla. */
  const anadirWidgetAqui = async (payload: PanelMetricaRequestDto) => {
    setErrorWidgets(null)
    setMensajeWidgets(null)
    setGuardandoWidget(true)
    try {
      await anadirWidgetApi(panelId ?? '', payload)
      setFormWidgetAbierto(false)
      setMensajeWidgets('Widget añadido al dashboard.')
      await cargarDashboard(filtrosAplicados, false)
    } catch (err) {
      // El backend rechaza la misma métrica dos veces en el mismo panel; su
      // mensaje ya lo explica, así que se muestra tal cual.
      setErrorWidgets(err instanceof Error ? err.message : 'No se pudo añadir el widget.')
    } finally {
      setGuardandoWidget(false)
    }
  }

  /**
   * Guarda los cambios del widget y refresca la tarjeta al momento.
   *
   * <p>Antes, las acciones del menú llevaban a la pantalla de widgets: el
   * usuario editaba allí y tenía que volver al dashboard para ver el efecto.
   * Con anchos y agrupaciones eso se leía como «no se ha guardado», porque la
   * tarjeta que estaba mirando no cambiaba.
   *
   * <p>Se recargan las tres cosas que dependen del cambio: los datos del
   * dashboard (que traen el ancho y la forma ya resueltos), la definición en
   * crudo de los widgets y la metadata.
   */
  const guardarEdicionWidget = async (
    panelMetricaId: number,
    presentacion: PanelMetricaRequestDto,
    resultado: PanelMetricaConfiguracionWidgetRequestDto | null,
  ) => {
    setErrorWidgets(null)
    setMensajeWidgets(null)
    setGuardandoWidget(true)

    const anchoAnterior = widgetsRaw.find((w) => w.id === panelMetricaId)?.ancho ?? null

    try {
      await actualizarWidget(panelId ?? '', panelMetricaId, presentacion)
      if (resultado) {
        await actualizarConfiguracionWidget(panelId ?? '', panelMetricaId, resultado)
      }

      setWidgetEnEdicion(null)
      await refrescarTrasEditar()

      const seAmplio = anchoAnterior != null && (presentacion.ancho ?? 0) > anchoAnterior
      setMensajeWidgets(
        seAmplio
          ? 'Indicador actualizado correctamente. Se ha ajustado el tamaño para que el resultado sea legible.'
          : 'Indicador actualizado correctamente.',
      )
    } catch (err) {
      setErrorWidgets(err instanceof Error ? err.message : 'No se pudo actualizar el indicador.')
    } finally {
      setGuardandoWidget(false)
    }
  }

  /**
   * Recarga lo que depende de la configuración de los widgets. El dashboard va
   * primero porque es lo que se ve; metadata y definición en crudo alimentan el
   * formulario y el menú.
   */
  const refrescarTrasEditar = async () => {
    await cargarDashboard(filtrosAplicados, false)
    try {
      const [crudos, meta] = await Promise.all([
        listarWidgets(panelId ?? ''),
        obtenerDashboardMetadata(panelId ?? ''),
      ])
      setWidgetsRaw(crudos)
      setMetadataWidgets(
        Object.fromEntries(meta.widgets.map((w) => [w.panelMetricaId, w.tipoResultadosPermitidos])),
      )
      setMetaCompleta(Object.fromEntries(meta.widgets.map((w) => [w.panelMetricaId, w])))
      setConfigPorWidget(
        Object.fromEntries(meta.widgets.map((w) => [w.panelMetricaId, w.configuracionWidgetActual])),
      )
    } catch {
      // La tarjeta ya se ha refrescado con `cargarDashboard`; que falle el
      // refresco auxiliar no debe deshacer el guardado ni alarmar al usuario.
    }
  }

  /** Quita un widget del panel. La métrica sigue en el catálogo del dataset. */
  const quitarWidgetAqui = async (panelMetricaId: number, titulo: string) => {
    if (!window.confirm(`¿Quitar «${titulo}» de este dashboard? La métrica seguirá en el catálogo.`)) {
      return
    }
    setErrorWidgets(null)
    setMensajeWidgets(null)
    try {
      await quitarWidget(panelId ?? '', panelMetricaId)
      setMensajeWidgets('Widget quitado del dashboard.')
      await cargarDashboard(filtrosAplicados, false)
    } catch (err) {
      setErrorWidgets(err instanceof Error ? err.message : 'No se pudo quitar el widget.')
    }
  }

  /**
   * Acciones del menú «⋮» de una tarjeta. Editar y cambiar tamaño llevan a la
   * pantalla de widgets, que es donde vive el formulario completo; quitar se
   * resuelve aquí mismo porque no necesita más contexto.
   */
  const accionesDeWidget = (
    panelMetricaId: number,
    titulo: string,
    admiteAgrupar: boolean,
  ): OpcionMenuWidget[] => {
    const abrirEdicion = (enfocarAgrupacion: boolean) => {
      setErrorWidgets(null)
      setMensajeWidgets(null)
      setWidgetEnEdicion({ id: panelMetricaId, enfocarAgrupacion })
    }

    return [
      {
        etiqueta: 'Editar visualización',
        onSeleccionar: () => abrirEdicion(false),
      },
      {
        etiqueta: 'Agrupar o segmentar',
        onSeleccionar: () => abrirEdicion(true),
        deshabilitada: !admiteAgrupar,
        motivoDeshabilitada: admiteAgrupar
          ? undefined
          : 'Este indicador no admite agrupación: su resultado es un reparto o una etiqueta.',
      },
      {
        etiqueta: 'Cambiar tamaño',
        onSeleccionar: () => abrirEdicion(false),
      },
      {
        etiqueta: 'Quitar del dashboard',
        destructiva: true,
        onSeleccionar: () => quitarWidgetAqui(panelMetricaId, titulo),
      },
    ]
  }

  // El catálogo y la metadata son auxiliares: si fallan, los filtros siguen
  // funcionando y el dashboard no se bloquea.
  useEffect(() => {
    const controller = new AbortController()
    getCatalogo(controller.signal)
      .then(setCatalogo)
      .catch(() => {
        if (!controller.signal.aborted) setCatalogo(null)
      })
    obtenerDashboardMetadata(panelId ?? '', controller.signal)
      .then(async (m) => {
        setCamposFecha(m.camposFechaPermitidos)
        setMetadataWidgets(
          Object.fromEntries(m.widgets.map((w) => [w.panelMetricaId, w.tipoResultadosPermitidos])),
        )
        setMetaCompleta(Object.fromEntries(m.widgets.map((w) => [w.panelMetricaId, w])))
        setConfigPorWidget(
          Object.fromEntries(m.widgets.map((w) => [w.panelMetricaId, w.configuracionWidgetActual])),
        )

        // Las métricas DISTRIBUCION llevan su campo de agrupación en la propia
        // definición de la métrica, no en el resultado: sin esto no se puede
        // saber qué campo representa cada categoría de esos widgets.
        listarMetricas(m.dataset.id, controller.signal)
          .then((metricas) =>
            setCampoAgrupacionPorMetrica(
              Object.fromEntries(metricas.map((mt) => [mt.id, mt.configuracion?.campoAgrupacion ?? null])),
            ),
          )
          .catch(() => {
            // Sin esto, las distribuciones simplemente no serán seleccionables.
          })

        try {
          const datasetId = m.dataset.id
          const fm = await obtenerFrontendMetadata(datasetId, controller.signal)
          setEtiquetasCampos(Object.fromEntries(fm.campos.map((c) => [c.codigo, c.etiqueta])))

          // El identificador de individuo se resuelve aparte de los campos de
          // agrupación: agrupar por HC daría una categoría por paciente, que
          // no es una comparación útil.
          const individuo = detectarCampoIndividuo(
            fm.campos.filter((c) => c.activo && c.roles.filtrable && c.tipoDato === 'TEXTO'),
          )

          const categoricos = fm.campos.filter(
            (c) =>
              c.activo &&
              c.roles.filtrable &&
              c.codigo !== individuo?.codigo &&
              !CAMPOS_FECHA_O_NUMERICOS_EXCLUIDOS.has(c.tipoDato),
          )
          const conValores: CampoFiltroCategoria[] = await Promise.all(
            categoricos.map(async (c) => ({
              codigo: c.codigo,
              etiqueta: c.etiqueta,
              tipoDato: c.tipoDato,
              valores: await listarValoresUnicosDeCampo(datasetId, c.codigo, controller.signal),
            })),
          )
          // Solo se ofrecen campos que realmente tengan valores en los datos:
          // un selector vacío no aporta nada y suma ruido.
          setCampos(clasificarCampos(conValores.filter((c) => c.valores.length > 0)))

          if (individuo) {
            const valores = await listarValoresUnicosDeCampo(datasetId, individuo.codigo, controller.signal)
            setTienePaciente(valores.length > 0)
            setValoresPaciente(valores)
            setCampoIndividuo(valores.length > 0 ? { ...individuo, valores } : null)
          }
        } catch (err) {
          if (!controller.signal.aborted) {
            setErrorCamposCategoria(err instanceof Error ? err.message : 'error desconocido')
          }
        }
      })
      .catch((err: unknown) => {
        if (!controller.signal.aborted) {
          setErrorMetadata(err instanceof Error ? err.message : 'error desconocido')
        }
      })
    listarWidgets(panelId ?? '', controller.signal)
      .then(setWidgetsRaw)
      .catch(() => {
        // Si falla, el selector de vista simplemente no se ofrece (ver
        // DashboardWidgetRenderer: sin onCambiarVisualizacion no hay riesgo,
        // solo se pierde la posibilidad de cambiar la vista hasta recargar).
      })
    return () => controller.abort()
  }, [panelId])

  /**
   * IDs de los widgets que el dashboard base está mostrando ahora mismo: el
   * conjunto que la respuesta cruzada tiene que cubrir por completo.
   * Referencia estable mientras `datos` no cambie, para no reejecutar el cruce.
   */
  const idsWidgetsBase = useMemo(() => (datos?.widgets ?? []).map((w) => w.panelMetricaId), [datos])

  // Recalcula el dashboard CRUZADO (globales AND selección). El base se
  // conserva intacto: el widget de origen sigue mostrando todas sus categorías.
  useEffect(() => {
    if (!seleccionGrafica) {
      setDatosCruzados(null)
      setErrorCruce(null)
      return
    }

    const controller = new AbortController()
    const idPeticion = ++peticionCruceRef.current
    setCruzando(true)
    setErrorCruce(null)

    const request = aRequest(filtrosAplicados)
    ejecutarDashboard(
      panelId ?? '',
      { ...request, filtros: combinarFiltros(request.filtros ?? [], seleccionGrafica) },
      controller.signal,
    )
      .then((respuesta) => {
        // Un clic rápido en otra categoría no debe dejar que la respuesta
        // anterior sobrescriba la selección actual.
        if (idPeticion !== peticionCruceRef.current) return

        // All-or-nothing: o se aplican todos los widgets recalculados, o
        // ninguno. Rellenar los que falten con su resultado base mezclaría dos
        // poblaciones distintas en la misma pantalla.
        const integridad = validarIntegridadCruce(idsWidgetsBase, respuesta.widgets)
        if (!integridad.completa) {
          setErrorCruce('No se pudieron actualizar todos los indicadores.')
          setDatosCruzados(null)
          return
        }

        setDatosCruzados(respuesta)
      })
      .catch(() => {
        if (idPeticion === peticionCruceRef.current && !controller.signal.aborted) {
          setErrorCruce('No se pudieron actualizar los indicadores.')
          // Sin mezcla parcial: se vuelve al dashboard base completo.
          setDatosCruzados(null)
        }
      })
      .finally(() => {
        if (idPeticion === peticionCruceRef.current) setCruzando(false)
      })

    return () => controller.abort()
  }, [seleccionGrafica, filtrosAplicados, panelId, reintentoCruce, idsWidgetsBase])

  const quitarSeleccion = () => setSeleccionGrafica(null)

  const limpiar = () => {
    // Cambiar el contexto global cambia la población base: la selección
    // anterior puede referirse a un valor que ya no existe.
    setSeleccionGrafica(null)
    setFiltros(FILTROS_VACIOS)
    cargarDashboard(FILTROS_VACIOS, false)
  }

  // Aplica un conjunto concreto de filtros al instante (quitar una chip, quitar
  // el paciente), sin esperar a que el usuario pulse "Aplicar filtros".
  const aplicarValores = (nuevosValores: ValoresFiltros) => {
    setSeleccionGrafica(null)
    setFiltros(nuevosValores)
    cargarDashboard(nuevosValores, false)
  }

  const aplicarFiltrosGlobales = () => {
    setSeleccionGrafica(null)
    cargarDashboard(filtros, false)
  }

  // Cambia solo tipoVisualizacion de un widget, conservando el resto de su
  // configuración (metricaId, título/descripción, orden, ancho) tal cual
  // estaba. No crea ni duplica nada: reutiliza el mismo panelMetricaId.
  const cambiarVisualizacionWidget = async (panelMetricaId: number, nuevoTipo: TipoVisualizacion) => {
    const actual = widgetsRaw.find((w) => w.id === panelMetricaId)
    if (!actual) {
      throw new Error('No se encontró la configuración de este widget. Recarga la página e inténtalo de nuevo.')
    }
    const actualizado = await actualizarWidget(panelId ?? '', panelMetricaId, {
      metricaId: actual.metricaId,
      tituloPersonalizado: actual.tituloPersonalizado,
      descripcionPersonalizada: actual.descripcionPersonalizada,
      tipoVisualizacion: nuevoTipo,
      orden: actual.orden,
      ancho: actual.ancho,
    })
    setWidgetsRaw((filas) => filas.map((w) => (w.id === panelMetricaId ? actualizado : w)))
    await cargarDashboard(filtros, false)
  }

  // Campos ofrecidos como "Agrupar por" en la exploración local: los mismos
  // que ya se validaron para los filtros (categóricos, con valores reales,
  // sin el identificador de individuo).
  const camposAgrupables = useMemo(
    () => [...campos.principales, ...campos.avanzados],
    [campos],
  )

  // Filtros globales YA aplicados, en el formato del backend: son los que cada
  // widget combinará en AND con su filtro local.
  const filtrosGlobalesAplicados = useMemo(
    () => aRequest(filtrosAplicados).filtros ?? [],
    [filtrosAplicados],
  )

  /** Filtros del subconjunto: los MISMOS que el dashboard cruzado. */
  const filtrosSubconjunto = useMemo(
    () => filtrosDelSubconjunto(filtrosGlobalesAplicados, seleccionGrafica),
    [filtrosGlobalesAplicados, seleccionGrafica],
  )
  const firmaSubconjunto = firmaFiltros(filtrosSubconjunto)

  // Solo el resumen (barato) al crear una selección: las tablas y el perfil se
  // piden cuando el usuario abre el detalle.
  useEffect(() => {
    if (!seleccionGrafica) {
      setResumenSub(null)
      setDetalleAbierto(false)
      return
    }

    const controller = new AbortController()
    resumenSubconjunto(
      panelId ?? '',
      { filtros: filtrosSubconjunto, campoIndividuo: campoIndividuo?.codigo ?? null },
      controller.signal,
    )
      .then(setResumenSub)
      .catch(() => {
        // El resumen es informativo: si falla, el dashboard sigue igual.
        if (!controller.signal.aborted) setResumenSub(null)
      })

    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [firmaSubconjunto, panelId, campoIndividuo])


  // Individuo fijado por el contexto global, si lo hay. Se lee de los filtros
  // APLICADOS (no del formulario) y se compara con el código de campo realmente
  // detectado, no con un "pacienteCodigo" asumido.
  /**
   * Widgets que se pintan, resueltos de una sola vez:
   *  - sin selección o sin cruce válido → TODOS del dashboard base;
   *  - con cruce válido → el de origen en base (conserva sus categorías) y el
   *    resto cruzados.
   *
   * Se itera siempre sobre `datos.widgets`, así que el conjunto y el orden los
   * fija el base: un widget inesperado en la respuesta cruzada queda fuera sin
   * alterar nada.
   *
   * Si al montar la lista faltara cualquier cruzado, se devuelve el base
   * ENTERO: nunca se rellena hueco a hueco. `validarIntegridadCruce` ya lo
   * impide antes de guardar el estado, así que esta rama es una segunda
   * barrera — pero se escribe como all-or-nothing y no como fallback por
   * widget, porque un fallback individual mezclaría dos poblaciones distintas
   * en la misma pantalla.
   */
  const widgetsAMostrar = useMemo(() => {
    const base = datos?.widgets ?? []
    if (!seleccionGrafica || !datosCruzados) return base

    const cruzados = new Map(datosCruzados.widgets.map((w) => [w.panelMetricaId, w]))
    const resueltos: DashboardWidgetDto[] = []

    for (const w of base) {
      if (w.panelMetricaId === seleccionGrafica.widgetOrigenId) {
        resueltos.push(w)
        continue
      }
      const cruzado = cruzados.get(w.panelMetricaId)
      if (!cruzado) return base
      resueltos.push(cruzado)
    }

    return resueltos
  }, [datos, datosCruzados, seleccionGrafica])

  /**
   * Nombre legible del campo de fecha para el chip temporal. Se busca entre los
   * campos reales del dataset; si no aparece (los campos FECHA se excluyen de
   * `camposAgrupables`), se usa el código, nunca un texto inventado.
   */
  const etiquetaDeCampoFecha = (codigoConfigurado: string | null) => {
    const codigo = codigoConfigurado ?? CAMPO_FECHA_POR_DEFECTO
    return etiquetasCampos[codigo] ?? codigo
  }

  /** Contexto global en texto, para la cabecera del detalle. */
  const descripcionContextoGlobal = useMemo(() => {
    const partes = filtrosGlobalesAplicados.map((f) => {
      const etiqueta = etiquetasCampos[f.campo] ?? f.campo
      return `${etiqueta}: ${formatearEtiquetaCategoria(String(f.valor ?? ''))}`
    })
    return partes.length > 0 ? partes.join(' · ') : null
  }, [filtrosGlobalesAplicados, etiquetasCampos])

  const individuoGlobal = useMemo(() => {
    if (!campoIndividuo) return null
    const filtro = filtrosGlobalesAplicados.find((f) => f.campo === campoIndividuo.codigo)
    return typeof filtro?.valor === 'string' && filtro.valor.trim() ? filtro.valor.trim() : null
  }, [campoIndividuo, filtrosGlobalesAplicados])

  return (
    <div className={styles.page}>
      <StateContainer loading={cargandoInicial} error={errorCarga && !datos ? errorCarga : null} empty={datos === null}>
        {datos && (
          <>
            <div className={styles.breadcrumbWrap}>
              <Breadcrumbs
                items={[
                  { label: 'Datasets', to: '/datasets' },
                  { label: datos.dataset.codigo, to: `/datasets/${datos.dataset.id}` },
                  { label: 'Paneles', to: `/datasets/${datos.dataset.id}/paneles` },
                  { label: `Dashboard de ${datos.panel.codigo}` },
                ]}
              />
            </div>

            <DashboardHeader
              panelNombre={datos.panel.nombre}
              panelCodigo={datos.panel.codigo}
              datasetNombre={datos.dataset.nombre}
              datasetId={datos.dataset.id}
              totalWidgets={datos.resumen.totalWidgets}
              widgetsOk={datos.resumen.widgetsOk}
              widgetsConError={datos.resumen.widgetsConError}
              esDashboardInicial={esDashboardInicial}
              configurarWidgetsHref={`/datasets/${datos.dataset.id}/paneles/${datos.panel.id}/widgets`}
            />

            {/* La cabecera ("Contexto del análisis" + badge de alcance) la
                renderiza DashboardFilters, para que título y badge compartan
                fila; Card aporta solo el contenedor. */}
            <Card className={styles.filtrosCard}>
              <DashboardFilters
                valores={filtros}
                aplicados={filtrosAplicados}
                onChange={setFiltros}
                onAplicar={aplicarFiltrosGlobales}
                onLimpiar={limpiar}
                onAplicarValores={aplicarValores}
                granularidades={catalogo?.granularidades ?? []}
                camposFechaPermitidos={camposFecha}
                errorMetadata={errorMetadata}
                cargando={aplicando}
                tienePaciente={tienePaciente}
                valoresPaciente={valoresPaciente}
                camposPrincipales={campos.principales}
                camposAvanzados={campos.avanzados}
                errorCamposCategoria={errorCamposCategoria}
              />
            </Card>

            <SeleccionGraficaPanel
              seleccion={seleccionGrafica}
              cargando={cruzando}
              error={errorCruce}
              onQuitar={quitarSeleccion}
              onReintentar={() => setReintentoCruce((r) => r + 1)}
              resumen={resumenSub}
              detalleAbierto={detalleAbierto}
              onExplorar={() => setDetalleAbierto((abierto) => !abierto)}
            />

            {seleccionGrafica && detalleAbierto && (
              <SubconjuntoPanel
                // La firma como key fuerza un remontaje al cambiar el
                // subconjunto: nunca se ve un instante de datos anteriores.
                key={firmaSubconjunto}
                panelId={panelId ?? ''}
                filtros={filtrosSubconjunto}
                resumen={resumenSub}
                descripcionSeleccion={descripcionSeleccion(seleccionGrafica)}
                descripcionContexto={descripcionContextoGlobal}
                onCerrar={() => setDetalleAbierto(false)}
              />
            )}

            {errorCarga && <ErrorBanner mensaje={errorCarga} />}
            <ErrorBanner mensaje={errorWidgets} />
            {mensajeWidgets && <p className={styles.mensajeWidgets}>{mensajeWidgets}</p>}

            {/* Barra de widgets: encima de los gráficos, que es donde se está
                mirando cuando surge la necesidad de añadir uno. "Configurar
                widgets" sigue existiendo, pero como acceso secundario. */}
            <div className={styles.barraWidgets}>
              <span className={styles.barraTitulo}>Indicadores y gráficos</span>
              <div className={styles.barraAcciones}>
                <button
                  type="button"
                  className="btn btnPrimary"
                  onClick={() => {
                    setMensajeWidgets(null)
                    setFormWidgetAbierto((v) => !v)
                  }}
                >
                  + Añadir indicador o gráfico
                </button>
                <Link
                  className="btn btnSecondary"
                  to={`/datasets/${datos.dataset.id}/metricas/nueva/desde-columna?panelId=${datos.panel.id}`}
                >
                  + Crear indicador
                </Link>
                <Link
                  className="btn btnSecondary"
                  to={`/datasets/${datos.dataset.id}/paneles/${datos.panel.id}/widgets`}
                >
                  Organizar
                </Link>
              </div>
            </div>

            {/* Edición del widget SIN salir del dashboard: al guardar, la
                tarjeta de abajo se refresca sola. */}
            {widgetEnEdicion &&
              (() => {
                const crudo = widgetsRaw.find((w) => w.id === widgetEnEdicion.id)
                if (!crudo || !catalogoWidgets) return null
                return (
                  <Card title={`Editar: ${crudo.tituloPersonalizado ?? crudo.metricaNombre}`}>
                    <WidgetEditarForm
                      widget={crudo}
                      meta={metaCompleta[widgetEnEdicion.id]}
                      tipoVisualizaciones={catalogoWidgets.catalogo.tipoVisualizaciones}
                      camposFechaPermitidos={camposFecha ?? []}
                      camposAgrupacionPermitidos={camposAgrupables.map((c) => c.codigo)}
                      granularidades={catalogoWidgets.catalogo.granularidades}
                      enfocarAgrupacion={widgetEnEdicion.enfocarAgrupacion}
                      onGuardar={(presentacion, resultado) =>
                        guardarEdicionWidget(widgetEnEdicion.id, presentacion, resultado)
                      }
                      onCancelar={() => setWidgetEnEdicion(null)}
                      guardando={guardandoWidget}
                    />
                  </Card>
                )
              })()}

            {formWidgetAbierto && catalogoWidgets && (
              <Card title="Añadir indicador o gráfico">
                <WidgetForm
                  valorInicial={{
                    metricaId: '',
                    tituloPersonalizado: '',
                    descripcionPersonalizada: '',
                    tipoVisualizacion: '',
                    orden: '',
                    ancho: '',
                  }}
                  metricasDisponibles={catalogoWidgets.metricas}
                  tipoVisualizaciones={catalogoWidgets.catalogo.tipoVisualizaciones}
                  datasetId={String(datos.dataset.id)}
                  panelId={String(datos.panel.id)}
                  siguienteOrden={Math.max(0, ...datos.widgets.map((w) => w.orden ?? 0)) + 1}
                  esEdicion={false}
                  onSubmit={anadirWidgetAqui}
                  onCancelar={() => setFormWidgetAbierto(false)}
                  guardando={guardandoWidget}
                />
              </Card>
            )}

            {datos.widgets.length > 0 &&
              !aplicando &&
              hayFiltrosActivos(filtrosAplicados) &&
              datos.widgets.every((w) => widgetSinDatos(w) || widgetTodoCero(w)) && (
                <div className={styles.sinResultados}>
                  <h2 className={styles.sinResultadosTitulo}>
                    No hay registros para los filtros seleccionados.
                  </h2>
                  <p className={styles.sinResultadosTexto}>
                    Prueba a quitar algún filtro o restablecer el análisis completo.
                  </p>
                  <button type="button" className="btn btnPrimary" onClick={limpiar}>
                    Limpiar filtros
                  </button>
                </div>
              )}

            {datos.widgets.length === 0 ? (
              <div>
                <p className="stateEmpty">
                  Este panel no tiene widgets activos. Configura los widgets para ver resultados.
                </p>
                <Link
                  className="btn btnPrimary"
                  to={`/datasets/${datos.dataset.id}/paneles/${datos.panel.id}/widgets`}
                >
                  Configurar widgets
                </Link>
              </div>
            ) : (
              <div className={`${styles.grid} ${aplicando || cruzando ? styles.gridCargando : ''}`}>
                {widgetsAMostrar.map((widget) => (
                  <DashboardWidgetRenderer
                    key={widget.panelMetricaId}
                    widget={widget}
                    accionesMenu={accionesDeWidget(
                      widget.panelMetricaId,
                      widget.titulo,
                      (metadataWidgets[widget.panelMetricaId]?.length ?? 1) > 1,
                    )}
                    onCambiarVisualizacion={cambiarVisualizacionWidget}
                    camposAgrupables={camposAgrupables}
                    campoIndividuo={campoIndividuo}
                    filtrosGlobales={filtrosGlobalesAplicados}
                    fechaDesde={filtrosAplicados.fechaDesde || null}
                    fechaHasta={filtrosAplicados.fechaHasta || null}
                    campoFecha={configPorWidget[widget.panelMetricaId]?.campoFecha ?? null}
                    campoSegmentacion={configPorWidget[widget.panelMetricaId]?.campoSegmentacion ?? null}
                    campoAgrupacionPersistido={configPorWidget[widget.panelMetricaId]?.campoAgrupacion ?? null}
                    individuoGlobal={individuoGlobal}
                    seleccionGrafica={seleccionGrafica}
                    campoAgrupacionMetrica={campoAgrupacionPorMetrica[widget.metricaId] ?? null}
                    camposFechaPermitidos={camposFecha ?? []}
                    etiquetaCampoFecha={etiquetaDeCampoFecha(
                      configPorWidget[widget.panelMetricaId]?.campoFecha ?? null,
                    )}
                    onSeleccionar={(nueva) => setSeleccionGrafica((actual) => alternarSeleccion(actual, nueva))}
                    onSeleccionInvalidada={(origenId) =>
                      setSeleccionGrafica((actual) => (actual?.widgetOrigenId === origenId ? null : actual))
                    }
                  />
                ))}
              </div>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
