import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import type {
  CatalogoFrontendResponseDto,
  ConfiguracionMetricaDto,
  ConfiguracionWidgetDto,
  FiltroMetricaDto,
  Granularidad,
  MetadataMetricasResponseDto,
  MetricaClinicaRequestDto,
  MetricaClinicaResponseDto,
  PanelClinicoResponseDto,
  ResultadoMetricaResponseDto,
  TipoMetrica,
  TipoVisualizacion,
} from '../api/types'
import { getCatalogo } from '../api/frontendCatalogApi'
import { actualizarMetrica, crearMetrica, obtenerMetadataMetricas, obtenerMetrica, previewMetrica } from '../api/metricasApi'
import { actualizarConfiguracionWidget, anadirWidget, listarPaneles } from '../api/panelesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { FormField } from '../components/FormField'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { avisoCodigo } from '../utils/validacion'
import { MetricaConfigForm } from '../components/metrics/MetricaConfigForm'
// Las conversiones de filtros son las mismas en los tres modos de creación:
// viven en un único sitio para que no puedan divergir.
import { filtrosParaFormulario, filtrosParaPayload } from '../components/metrics/constructorMetrica'
import { WidgetActual } from '../components/widgets/WidgetActual'
import type { PlanResultado } from '../components/dashboard/visualizacionesCompatibles'
import { visualizacionesSegunPlan } from '../components/dashboard/visualizacionesCompatibles'
import styles from './MetricaFormPage.module.css'

interface DatosFormulario {
  catalogo: CatalogoFrontendResponseDto
  metadata: MetadataMetricasResponseDto
  metricaExistente: MetricaClinicaResponseDto | null
  paneles: PanelClinicoResponseDto[]
}

interface EstadoFormulario {
  codigo: string
  nombre: string
  descripcion: string
  tipoMetrica: TipoMetrica
  unidad: string
  decimales: string
  orden: string
  configuracion: ConfiguracionMetricaDto
}

const ANCHOS = [
  { valor: '3', etiqueta: 'Pequeño' },
  { valor: '6', etiqueta: 'Medio' },
  { valor: '12', etiqueta: 'Ancho completo' },
]

/**
 * Configuración vacía de partida según la operación.
 *
 * Las operaciones se agrupan por lo que necesitan, no una por una: las que
 * agregan un valor piden `campoValor`, las que reparten piden `campoAgrupacion`
 * y el porcentaje pide numerador y denominador. Así, ampliar el motor no
 * obliga a tocar este formulario.
 */
function configuracionBase(tipo: TipoMetrica): ConfiguracionMetricaDto {
  if (tipo === 'PORCENTAJE') {
    return { numerador: { filtros: [] }, denominador: { filtros: [] } }
  }
  if (tipo === 'DISTRIBUCION' || tipo === 'CATEGORIA_PRINCIPAL') {
    return { campoAgrupacion: null, filtros: [] }
  }
  if (tipo === 'CONTEO') {
    return { filtros: [] }
  }
  // PROMEDIO, SUMA, MEDIANA, MINIMO, MAXIMO, CONTEO_DISTINTO y COMPLETITUD
  // operan todas sobre un único campo.
  return { campoValor: null, filtros: [] }
}

function configuracionParaFormulario(tipo: TipoMetrica, config: ConfiguracionMetricaDto): ConfiguracionMetricaDto {
  if (tipo === 'PORCENTAJE') {
    return {
      numerador: { filtros: filtrosParaFormulario(config.numerador?.filtros) },
      denominador: { filtros: filtrosParaFormulario(config.denominador?.filtros) },
    }
  }
  return {
    ...configuracionBase(tipo),
    campoValor: config.campoValor ?? null,
    campoAgrupacion: config.campoAgrupacion ?? null,
    filtros: filtrosParaFormulario(config.filtros),
  }
}

export function MetricaFormPage() {
  const { datasetId, metricaId } = useParams<{ datasetId: string; metricaId?: string }>()
  const navigate = useNavigate()
  const esEdicion = metricaId !== undefined

  const { data, loading, error } = useApiResource<DatosFormulario>(
    async (signal) => {
      const [catalogo, metadata, metricaExistente, paneles] = await Promise.all([
        getCatalogo(signal),
        obtenerMetadataMetricas(datasetId ?? '', signal),
        metricaId ? obtenerMetrica(metricaId, signal) : Promise.resolve(null),
        listarPaneles(datasetId ?? '', signal),
      ])
      return { catalogo, metadata, metricaExistente, paneles }
    },
    [datasetId, metricaId],
  )

  const [form, setForm] = useState<EstadoFormulario>({
    codigo: '',
    nombre: '',
    descripcion: '',
    tipoMetrica: 'CONTEO',
    unidad: '',
    decimales: '',
    orden: '',
    configuracion: configuracionBase('CONTEO'),
  })
  const [erroresForm, setErroresForm] = useState<Record<string, string>>({})
  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [resultadoPreview, setResultadoPreview] = useState<ResultadoMetricaResponseDto | null>(null)
  const [fechaDesde, setFechaDesde] = useState('')
  const [fechaHasta, setFechaHasta] = useState('')
  const [guardando, setGuardando] = useState(false)

  // --- Paso "cómo quieres verlo" + "cómo se visualiza" + "añadir al dashboard" ---
  // Solo aplica al crear una métrica nueva: al editar una ya existente puede
  // estar en varios widgets/paneles a la vez, así que no tiene sentido pedir
  // "en qué panel la añado" — eso se sigue haciendo desde "Configurar widgets".
  const [modoResultado, setModoResultado] = useState<PlanResultado>('UNICO')
  const [campoAgrupacionWidget, setCampoAgrupacionWidget] = useState('')
  const [granularidadWidget, setGranularidadWidget] = useState('')
  const [campoFechaWidget, setCampoFechaWidget] = useState('')
  const [campoSegmentacionWidget, setCampoSegmentacionWidget] = useState('')
  const [tipoVisualizacionElegida, setTipoVisualizacionElegida] = useState('')
  // Desmarcado a propósito: crear una métrica y publicarla en un dashboard son
  // dos decisiones distintas, y la segunda no debe ocurrir por omisión.
  const [anadirADashboard, setAnadirADashboard] = useState(false)
  const [panelIdElegido, setPanelIdElegido] = useState('')
  const [anchoElegido, setAnchoElegido] = useState('3')
  const [erroresPlan, setErroresPlan] = useState<Record<string, string>>({})

  useEffect(() => {
    const metrica = data?.metricaExistente
    if (metrica) {
      const tipo = metrica.tipoMetrica as TipoMetrica
      setForm({
        codigo: metrica.codigo,
        nombre: metrica.nombre,
        descripcion: metrica.descripcion ?? '',
        tipoMetrica: tipo,
        unidad: metrica.unidad ?? '',
        decimales: String(metrica.decimales ?? ''),
        orden: String(metrica.orden ?? ''),
        configuracion: configuracionParaFormulario(tipo, metrica.configuracion),
      })
    }
  }, [data])

  // Preselecciona un panel (el "dashboard inicial" si existe) en cuanto se
  // conocen los paneles del dataset, para que el caso más común (añadir a un
  // único dashboard ya existente) no exija ni un clic de más.
  useEffect(() => {
    if (esEdicion || !data || data.paneles.length === 0 || panelIdElegido !== '') return
    const inicial = data.paneles.find((p) => p.codigo.startsWith('dashboard_inicial'))
    setPanelIdElegido(String((inicial ?? data.paneles[0]).id))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data])

  // El modo "distribución" ya agrupa dentro de la propia métrica (campoAgrupacion
  // de configuracionBase), así que el paso "cómo quieres verlo" (agrupar/segmentar
  // a nivel de widget) no aplica — y tampoco lo admite el backend (ver
  // MetricaAnaliticaServiceImpl.validarTipoPermitido).
  // Ni un reparto ni una etiqueta se pueden poner en un eje: para ambos, el
  // paso "cómo quieres verlo" no aplica (y el backend también los rechaza en
  // serie temporal y comparativa).
  const admiteAgruparOSegmentar =
    form.tipoMetrica !== 'DISTRIBUCION' && form.tipoMetrica !== 'CATEGORIA_PRINCIPAL'
  const modoEfectivo: PlanResultado = admiteAgruparOSegmentar ? modoResultado : 'AGRUPADO'
  const opcionesVisualizacion = visualizacionesSegunPlan(modoEfectivo)

  // Si cambia el modo (o el tipo de métrica cambia a DISTRIBUCION), la
  // visualización elegida puede dejar de ser válida: se reinicia a la primera
  // opción compatible en vez de dejar seleccionado algo que ya no aplica.
  useEffect(() => {
    if (!opcionesVisualizacion.some((o) => o.valor === tipoVisualizacionElegida)) {
      setTipoVisualizacionElegida(opcionesVisualizacion[0]?.valor ?? '')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modoEfectivo])

  const cambiarTipo = (tipo: TipoMetrica) => {
    setForm((f) => ({ ...f, tipoMetrica: tipo, configuracion: configuracionBase(tipo) }))
    setResultadoPreview(null)
    if (tipo === 'DISTRIBUCION' || tipo === 'CATEGORIA_PRINCIPAL') setModoResultado('UNICO')
  }

  const validar = (): Record<string, string> => {
    const errores: Record<string, string> = {}
    if (!form.codigo.trim()) errores.codigo = 'El código es obligatorio.'
    if (!form.nombre.trim()) errores.nombre = 'El nombre es obligatorio.'

    const config = form.configuracion
    const requiereAgrupacion = form.tipoMetrica === 'DISTRIBUCION' || form.tipoMetrica === 'CATEGORIA_PRINCIPAL'
    const requiereValor =
      form.tipoMetrica !== 'CONTEO' && form.tipoMetrica !== 'PORCENTAJE' && !requiereAgrupacion

    if (requiereValor && !config.campoValor) {
      errores['configuracion.campoValor'] = 'Selecciona el campo sobre el que se calcula.'
    }
    if (requiereAgrupacion && !config.campoAgrupacion) {
      errores['configuracion.campoAgrupacion'] = 'Selecciona el campo de agrupación.'
    }

    const operadores = data?.catalogo.operadoresFiltro ?? []
    const validarFiltros = (filtros: FiltroMetricaDto[], clave: string) => {
      for (const filtro of filtros) {
        if (!filtro.campo) {
          errores[clave] = 'Cada filtro debe indicar un campo.'
          return
        }
        if (!filtro.operador) {
          errores[clave] = 'Cada filtro debe indicar un operador.'
          return
        }
        const operador = operadores.find((o) => o.codigo === filtro.operador)
        if (operador?.requiereLista) {
          const texto = typeof filtro.valor === 'string' ? filtro.valor : ''
          if (!texto.split(',').some((s) => s.trim() !== '')) {
            errores[clave] = 'Los operadores de lista requieren al menos un valor.'
            return
          }
        } else if (operador?.requiereValor) {
          if (filtro.valor === null || filtro.valor === undefined || filtro.valor === '') {
            errores[clave] = 'Este operador requiere un valor.'
            return
          }
        }
      }
    }

    if (form.tipoMetrica === 'PORCENTAJE') {
      validarFiltros(config.numerador?.filtros ?? [], 'configuracion.numerador')
      validarFiltros(config.denominador?.filtros ?? [], 'configuracion.denominador')
    } else {
      validarFiltros(config.filtros ?? [], 'configuracion.filtros')
    }

    return errores
  }

  const validarPlan = (): Record<string, string> => {
    if (esEdicion) return {}
    const errores: Record<string, string> = {}
    if (admiteAgruparOSegmentar) {
      if (modoResultado === 'AGRUPADO' && !campoAgrupacionWidget) {
        errores['plan.agrupacion'] = 'Selecciona por qué campo quieres agrupar.'
      }
      if (modoResultado === 'SERIE' && !granularidadWidget) {
        errores['plan.granularidad'] = 'Selecciona cada cuánto tiempo agrupar (mes, trimestre o año).'
      }
    }
    if (!tipoVisualizacionElegida) {
      errores['plan.visualizacion'] = 'Selecciona cómo quieres verlo.'
    }
    if (anadirADashboard && !panelIdElegido) {
      errores['plan.panel'] = 'Selecciona en qué dashboard quieres añadirlo.'
    }
    return errores
  }

  const construirPayload = (): MetricaClinicaRequestDto => {
    const campos = data?.metadata.campos ?? []
    const operadores = data?.catalogo.operadoresFiltro ?? []
    const config = form.configuracion

    // El campo objetivo viaja en `campoValor` o en `campoAgrupacion` según lo
    // que exija la operación. Se decide con los mismos criterios que
    // `configuracionBase`, para que ampliar el motor no obligue a añadir aquí
    // un caso más por cada operación nueva.
    const usaAgrupacion = form.tipoMetrica === 'DISTRIBUCION' || form.tipoMetrica === 'CATEGORIA_PRINCIPAL'
    const usaValor =
      form.tipoMetrica !== 'CONTEO' && form.tipoMetrica !== 'PORCENTAJE' && !usaAgrupacion

    const configuracion: ConfiguracionMetricaDto =
      form.tipoMetrica === 'PORCENTAJE'
        ? {
            filtros: filtrosParaPayload(config.filtros ?? [], campos, operadores),
            numerador: { filtros: filtrosParaPayload(config.numerador?.filtros ?? [], campos, operadores) },
            denominador: { filtros: filtrosParaPayload(config.denominador?.filtros ?? [], campos, operadores) },
          }
        : {
            ...(usaValor ? { campoValor: config.campoValor } : {}),
            ...(usaAgrupacion ? { campoAgrupacion: config.campoAgrupacion } : {}),
            filtros: filtrosParaPayload(config.filtros ?? [], campos, operadores),
          }

    return {
      codigo: form.codigo.trim(),
      nombre: form.nombre.trim(),
      descripcion: form.descripcion.trim() || null,
      tipoMetrica: form.tipoMetrica,
      configuracion,
      unidad: form.unidad.trim() || null,
      decimales: form.decimales === '' ? null : Number(form.decimales),
      orden: form.orden === '' ? null : Number(form.orden),
    }
  }

  const previsualizar = async () => {
    setErrorBackend(null)
    setResultadoPreview(null)
    const errores = validar()
    setErroresForm(errores)
    if (Object.keys(errores).length > 0) return

    try {
      const resultado = await previewMetrica(datasetId ?? '', {
        metrica: construirPayload(),
        fechaDesde: fechaDesde || null,
        fechaHasta: fechaHasta || null,
      })
      setResultadoPreview(resultado)
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al previsualizar.')
    }
  }

  // Edición: se mantiene exactamente el comportamiento de siempre (solo
  // actualiza la métrica; los widgets que ya la muestran no se tocan aquí).
  const guardarEdicion = async () => {
    setErrorBackend(null)
    const errores = validar()
    setErroresForm(errores)
    if (Object.keys(errores).length > 0) return

    setGuardando(true)
    try {
      await actualizarMetrica(metricaId ?? '', construirPayload())
      navigate(`/datasets/${datasetId}/metricas`)
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al guardar la métrica.')
      setGuardando(false)
    }
  }

  // Creación: crea la métrica y, en el mismo paso, el widget que la muestra
  // en el dashboard elegido — con la agrupación/segmentación ya aplicada si
  // se pidió. Reutiliza únicamente endpoints que ya existían (crear métrica,
  // añadir widget, configurar resultado del widget): nada de esto es nuevo
  // en el backend, solo estaba repartido en tres pantallas distintas.
  const guardarYAnadir = async () => {
    setErrorBackend(null)
    const erroresBase = validar()
    const erroresPlanActual = validarPlan()
    setErroresForm(erroresBase)
    setErroresPlan(erroresPlanActual)
    if (Object.keys(erroresBase).length > 0 || Object.keys(erroresPlanActual).length > 0) return

    setGuardando(true)
    try {
      const metricaCreada = await crearMetrica(datasetId ?? '', construirPayload())

      if (!anadirADashboard || !panelIdElegido) {
        // Comportamiento por defecto: la métrica queda en el catálogo y no se
        // toca ningún panel.
        navigate(`/datasets/${datasetId}/metricas`)
        return
      }

      const widgetCreado = await anadirWidget(panelIdElegido, {
        metricaId: metricaCreada.id,
        tipoVisualizacion: tipoVisualizacionElegida as TipoVisualizacion,
        ancho: Number(anchoElegido),
        orden: null,
      })

      if (admiteAgruparOSegmentar && modoResultado !== 'UNICO') {
        const configuracionWidget: ConfiguracionWidgetDto =
          modoResultado === 'AGRUPADO'
            ? { campoAgrupacion: campoAgrupacionWidget, granularidad: null, campoFecha: null, campoSegmentacion: null }
            : {
                granularidad: granularidadWidget as Granularidad,
                campoFecha: campoFechaWidget || null,
                campoSegmentacion: campoSegmentacionWidget || null,
                campoAgrupacion: null,
              }

        await actualizarConfiguracionWidget(panelIdElegido, widgetCreado.id, {
          tipoResultado: modoResultado === 'AGRUPADO' ? 'COMPARATIVA' : 'SERIE_TEMPORAL',
          configuracionWidget,
        })
      }

      navigate(`/paneles/${panelIdElegido}/dashboard`)
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al guardar y añadir al dashboard.')
      setGuardando(false)
    }
  }

  const ayudaTipoMetrica = data?.catalogo.tipoMetricas.find((t) => t.codigo === form.tipoMetrica)?.descripcion
  const camposAgrupables = (data?.metadata.campos ?? []).filter((c) => c.utilizableComoCampoAgrupacion)
  const camposFecha = (data?.metadata.campos ?? []).filter((c) => c.utilizableComoCampoFecha)

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.metadata.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Métricas', to: `/datasets/${datasetId}/metricas` },
                { label: esEdicion ? 'Editar métrica' : 'Nueva métrica' },
              ]}
            />
            <h1>{esEdicion ? 'Editar métrica' : 'Nueva métrica'}</h1>
            <p>
              Dataset: <strong>{data.metadata.dataset.nombre}</strong> ({data.metadata.dataset.codigo})
            </p>

            <ErrorBanner mensaje={errorBackend} />

            <Card title={esEdicion ? 'Datos generales' : 'Paso 1 — Qué quieres medir'}>
              {!esEdicion && (
                <p className={styles.introPaso}>Define qué indicador quieres calcular sobre tus datos clínicos.</p>
              )}
              <div className={styles.formGrid}>
                <FormField
                  label="Código interno de la métrica"
                  help="Identificador técnico único dentro del dataset. Ejemplos: total_ilq, tasa_ilq, estancia_media."
                  aviso={avisoCodigo(form.codigo)}
                  error={erroresForm.codigo}
                >
                  <input value={form.codigo} onChange={(e) => setForm({ ...form, codigo: e.target.value })} />
                </FormField>
                <FormField label="Nombre" error={erroresForm.nombre}>
                  <input value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
                </FormField>
                <FormField label="Tipo de métrica" help={ayudaTipoMetrica ?? undefined}>
                  <select value={form.tipoMetrica} onChange={(e) => cambiarTipo(e.target.value as TipoMetrica)}>
                    {data.catalogo.tipoMetricas.map((t) => (
                      <option key={t.codigo} value={t.codigo}>
                        {t.nombre}
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField label="Unidad">
                  <input value={form.unidad} onChange={(e) => setForm({ ...form, unidad: e.target.value })} />
                </FormField>
                <FormField label="Decimales">
                  <input
                    type="number"
                    step={1}
                    min={0}
                    value={form.decimales}
                    onChange={(e) => setForm({ ...form, decimales: e.target.value })}
                  />
                </FormField>
                {esEdicion && (
                  <FormField label="Orden">
                    <input
                      type="number"
                      step={1}
                      value={form.orden}
                      onChange={(e) => setForm({ ...form, orden: e.target.value })}
                    />
                  </FormField>
                )}
                <div className={styles.descripcion}>
                  <FormField label="Descripción">
                    <textarea
                      rows={2}
                      value={form.descripcion}
                      onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                    />
                  </FormField>
                </div>
              </div>
            </Card>

            <Card title={esEdicion ? 'Configuración' : 'Filtros (opcional)'}>
              {!esEdicion && (
                <p className={styles.introPaso}>Puedes limitar el cálculo a un subconjunto de registros.</p>
              )}
              <MetricaConfigForm
                tipoMetrica={form.tipoMetrica}
                configuracion={form.configuracion}
                onChange={(configuracion) => setForm({ ...form, configuracion })}
                campos={data.metadata.campos}
                operadoresCatalogo={data.catalogo.operadoresFiltro}
                errores={erroresForm}
              />
            </Card>

            {!esEdicion && admiteAgruparOSegmentar && (
              <Card title="Paso 2 — ¿Cómo quieres verlo?">
                <p className={styles.introPaso}>
                  Puedes ver un único valor, desglosarlo por una categoría (por ejemplo "por sexo", "por
                  procedimiento" o "por ASA"), o seguir su evolución en el tiempo (por ejemplo "por mes",
                  opcionalmente separado "por mes y sexo").
                </p>
                <div className={styles.opcionesModo}>
                  <label className={styles.opcionModo}>
                    <input
                      type="radio"
                      name="modoResultado"
                      checked={modoResultado === 'UNICO'}
                      onChange={() => setModoResultado('UNICO')}
                    />
                    Un solo valor
                  </label>
                  <label className={styles.opcionModo}>
                    <input
                      type="radio"
                      name="modoResultado"
                      checked={modoResultado === 'AGRUPADO'}
                      onChange={() => setModoResultado('AGRUPADO')}
                    />
                    Agrupado por categoría
                  </label>
                  <label className={styles.opcionModo}>
                    <input
                      type="radio"
                      name="modoResultado"
                      checked={modoResultado === 'SERIE'}
                      onChange={() => setModoResultado('SERIE')}
                    />
                    Evolución en el tiempo
                  </label>
                </div>

                {modoResultado === 'AGRUPADO' && (
                  <>
                    <FormField
                      label="Agrupar por"
                      help="El resultado se calculará una vez por cada valor de este campo."
                      error={erroresPlan['plan.agrupacion']}
                    >
                      <select value={campoAgrupacionWidget} onChange={(e) => setCampoAgrupacionWidget(e.target.value)}>
                        <option value="">— seleccionar campo —</option>
                        {camposAgrupables.map((c) => (
                          <option key={c.codigo} value={c.codigo}>
                            {c.etiqueta}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <p className={styles.avisoPreviewAgrupado}>
                      La vista previa de abajo muestra el valor sin agrupar. La versión agrupada estará
                      disponible al ver el dashboard, después de guardar.
                    </p>
                  </>
                )}

                {modoResultado === 'SERIE' && (
                  <>
                    <div className={styles.formGrid}>
                      <FormField
                        label="Cada cuánto tiempo"
                        error={erroresPlan['plan.granularidad']}
                      >
                        <select value={granularidadWidget} onChange={(e) => setGranularidadWidget(e.target.value)}>
                          <option value="">— seleccionar —</option>
                          {data.catalogo.granularidades.map((g) => (
                            <option key={g.codigo} value={g.codigo}>
                              {g.nombre}
                            </option>
                          ))}
                        </select>
                      </FormField>
                      <FormField label="Campo de fecha (opcional)">
                        <select value={campoFechaWidget} onChange={(e) => setCampoFechaWidget(e.target.value)}>
                          <option value="">— por defecto —</option>
                          {camposFecha.map((c) => (
                            <option key={c.codigo} value={c.codigo}>
                              {c.etiqueta}
                            </option>
                          ))}
                        </select>
                      </FormField>
                      <FormField
                        label="Separar series por (opcional)"
                        help='Ejemplo: "por mes y sexo" — una línea distinta para cada sexo.'
                      >
                        <select value={campoSegmentacionWidget} onChange={(e) => setCampoSegmentacionWidget(e.target.value)}>
                          <option value="">— sin separar —</option>
                          {camposAgrupables.map((c) => (
                            <option key={c.codigo} value={c.codigo}>
                              {c.etiqueta}
                            </option>
                          ))}
                        </select>
                      </FormField>
                    </div>
                    <p className={styles.avisoPreviewAgrupado}>
                      La vista previa de abajo muestra el valor sin agrupar. La evolución en el tiempo estará
                      disponible al ver el dashboard, después de guardar.
                    </p>
                  </>
                )}
              </Card>
            )}

            <p className={styles.ayudaPreview}>Previsualiza el resultado antes de guardar la métrica.</p>
            <div className={styles.fechasPreview}>
              <span>Rango para previsualizar (opcional):</span>
              <input type="date" value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} />
              <span>—</span>
              <input type="date" value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} />
            </div>

            <div className={styles.botones}>
              <button type="button" className="btn btnSecondary" onClick={previsualizar}>
                Previsualizar
              </button>
              {!esEdicion && (
                <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas`}>
                  Cancelar
                </Link>
              )}
            </div>

            {resultadoPreview && (
              <Card title="Previsualización" subtitle={`${resultadoPreview.tipoMetrica}`}>
                <WidgetActual resultado={resultadoPreview} />
              </Card>
            )}

            {!esEdicion && (
              <Card title="Paso 3 — Cómo se visualiza">
                <p className={styles.introPaso}>Elige cómo quieres que se vea este indicador en el dashboard.</p>
                {erroresPlan['plan.visualizacion'] && (
                  <p className={styles.errorPlan}>{erroresPlan['plan.visualizacion']}</p>
                )}
                <div className={styles.opcionesModo}>
                  {opcionesVisualizacion.map((o) => (
                    <label key={o.valor} className={styles.opcionModo}>
                      <input
                        type="radio"
                        name="tipoVisualizacion"
                        checked={tipoVisualizacionElegida === o.valor}
                        onChange={() => setTipoVisualizacionElegida(o.valor)}
                      />
                      {o.etiqueta}
                    </label>
                  ))}
                </div>
              </Card>
            )}

            {!esEdicion && (
              <Card title="Paso 4 — Guardar">
                <p className={styles.introPaso}>
                  La métrica se guarda en el catálogo del dataset. Añadirla a un dashboard es opcional: puedes
                  hacerlo ahora o más tarde, desde «Configurar widgets» de cualquier panel.
                </p>

                <label className={styles.opcionDashboard}>
                  <input
                    type="checkbox"
                    checked={anadirADashboard}
                    onChange={(e) => setAnadirADashboard(e.target.checked)}
                    disabled={data.paneles.length === 0}
                  />
                  Añadir también a un dashboard
                  {data.paneles.length === 0 && (
                    <span className={styles.ayudaEnLinea}>
                      (este dataset todavía no tiene ninguno; créalo desde{' '}
                      <Link to={`/datasets/${datasetId}/paneles`}>Paneles</Link>)
                    </span>
                  )}
                </label>

                {anadirADashboard && data.paneles.length > 0 && (
                  <div className={styles.formGrid}>
                    <FormField label="Dashboard" error={erroresPlan['plan.panel']}>
                      <select value={panelIdElegido} onChange={(e) => setPanelIdElegido(e.target.value)}>
                        <option value="">— seleccionar —</option>
                        {data.paneles.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.nombre}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Tamaño del widget" help="Cuánto sitio ocupa en el dashboard.">
                      <select value={anchoElegido} onChange={(e) => setAnchoElegido(e.target.value)}>
                        {ANCHOS.map((a) => (
                          <option key={a.valor} value={a.valor}>
                            {a.etiqueta}
                          </option>
                        ))}
                      </select>
                    </FormField>
                  </div>
                )}

                <div className={styles.botones}>
                  <button type="button" className="btn btnPrimary" disabled={guardando} onClick={guardarYAnadir}>
                    {guardando ? 'Guardando…' : anadirADashboard ? 'Crear y añadir al dashboard' : 'Crear métrica'}
                  </button>
                </div>
              </Card>
            )}

            {esEdicion && (
              <div className={styles.botones}>
                <button type="button" className="btn btnPrimary" disabled={guardando} onClick={guardarEdicion}>
                  Guardar cambios
                </button>
                <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas`}>
                  Cancelar
                </Link>
              </div>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
