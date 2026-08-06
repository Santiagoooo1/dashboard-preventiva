import { useEffect, useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import type {
  FiltroMetricaDto,
  MetricaClinicaRequestDto,
  MetricaClinicaResponseDto,
  OperacionDisponibleDto,
  PerfilCampoDto,
  ResultadoMetricaResponseDto,
  TipoMetrica,
} from '../api/types'
import { getCatalogo } from '../api/frontendCatalogApi'
import { listarPaneles } from '../api/panelesApi'
import {
  crearMetrica,
  listarMetricas,
  obtenerMetadataMetricas,
  obtenerPerfilCampos,
  previewMetrica,
} from '../api/metricasApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { FormField } from '../components/FormField'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { FiltroBuilder } from '../components/metrics/FiltroBuilder'
import { SelectorCampoPerfil } from '../components/metrics/SelectorCampoPerfil'
import { WidgetActual } from '../components/widgets/WidgetActual'
import {
  construirConfiguracionDesdeColumna,
  describirCalculo,
  filtrosParaPayload,
  sugerirCodigo,
  sugerirNombre,
} from '../components/metrics/constructorMetrica'
import {
  visualizacionesDeDistribucion,
  visualizacionesSegunPlan,
} from '../components/dashboard/visualizacionesCompatibles'
import { ETIQUETA_ROL } from '../components/metrics/etiquetasRol'
import { MetricaCreadaPanel } from '../components/metrics/MetricaCreadaPanel'
import styles from './MetricaDesdeColumnaPage.module.css'

/** Top N por defecto cuando la operación lo exige por alta cardinalidad. */
const TOP_N_POR_DEFECTO = 10

/**
 * Constructor de métricas desde una columna importada (Fase 6.9I.2).
 *
 * <p>Recorrido: elegir columna → confirmar cómo interpretarla → elegir qué
 * calcular → acotar con filtros → decidir cómo verlo → guardar. En cada paso
 * solo se ofrece lo que el backend ha declarado compatible para esa columna:
 * la matriz de compatibilidad no se replica aquí.
 *
 * <p>Guardar la métrica y añadirla a un dashboard son dos acciones distintas.
 * La primera es la principal; la segunda, opcional y desmarcada por defecto.
 */
export function MetricaDesdeColumnaPage() {
  const { datasetId } = useParams<{ datasetId: string }>()
  // Contexto de navegación, no dato de la métrica: de qué panel venía el
  // usuario, para devolverlo allí sin pedírselo otra vez.
  const [searchParams] = useSearchParams()
  const panelOrigenId = searchParams.get('panelId')

  const { data, loading, error } = useApiResource(
    async (signal) => {
      const [perfil, catalogo, metadata, paneles, metricas] = await Promise.all([
        obtenerPerfilCampos(datasetId ?? '', signal),
        getCatalogo(signal),
        obtenerMetadataMetricas(datasetId ?? '', signal),
        listarPaneles(datasetId ?? '', signal),
        listarMetricas(datasetId ?? '', signal),
      ])
      return { perfil, catalogo, metadata, paneles, metricas }
    },
    [datasetId],
  )

  // --- Paso 1 y 2: campo y su interpretación ---
  const [codigoCampo, setCodigoCampo] = useState('')
  const [rolElegido, setRolElegido] = useState('')

  // --- Paso 3: operación ---
  const [operacionElegida, setOperacionElegida] = useState<TipoMetrica | ''>('')

  // --- Paso 4: filtros y base ---
  const [filtros, setFiltros] = useState<FiltroMetricaDto[]>([])
  const [incluirSinDato, setIncluirSinDato] = useState(true)
  const [topN, setTopN] = useState<string>('')

  // --- Paso 5: agrupación o periodo (nivel widget) ---
  const [modoResultado, setModoResultado] = useState<'UNICO' | 'AGRUPADO' | 'SERIE'>('UNICO')
  const [campoAgrupacionWidget, setCampoAgrupacionWidget] = useState('')
  const [granularidadWidget, setGranularidadWidget] = useState('')
  const [campoFechaWidget, setCampoFechaWidget] = useState('')

  // --- Paso 6 y 7: visualización y guardado ---
  const [visualizacion, setVisualizacion] = useState('')

  const [codigo, setCodigo] = useState('')
  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [decimales, setDecimales] = useState('')

  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [preview, setPreview] = useState<ResultadoMetricaResponseDto | null>(null)
  const [guardando, setGuardando] = useState(false)

  // Métrica ya guardada: mientras exista, la pantalla muestra el estado de
  // éxito con el paso siguiente en vez de devolver al listado.
  const [metricaCreada, setMetricaCreada] = useState<MetricaClinicaResponseDto | null>(null)
  const [abrirFormularioWidget, setAbrirFormularioWidget] = useState(false)

  const campo: PerfilCampoDto | null =
    data?.perfil.campos.find((c) => c.codigo === codigoCampo) ?? null

  // Operaciones del rol que el usuario tiene seleccionado, resueltas por el
  // backend: cambiar la interpretación cambia la oferta sin nuevas llamadas.
  const operaciones: OperacionDisponibleDto[] = useMemo(() => {
    if (!campo) return []
    return campo.operacionesPorRol?.[rolElegido] ?? campo.operaciones
  }, [campo, rolElegido])

  const operacion = operaciones.find((o) => o.codigo === operacionElegida) ?? null

  // Al elegir campo: se adopta el rol sugerido y se limpia todo lo que dependía
  // del campo anterior, para no arrastrar una operación que ya no aplica.
  const elegirCampo = (nuevoCodigo: string) => {
    setCodigoCampo(nuevoCodigo)
    const nuevoCampo = data?.perfil.campos.find((c) => c.codigo === nuevoCodigo) ?? null
    setRolElegido(nuevoCampo?.rolSugerido ?? '')
    setOperacionElegida('')
    setFiltros([])
    setTopN('')
    setPreview(null)
    setErrores({})
  }

  const cambiarRol = (nuevoRol: string) => {
    setRolElegido(nuevoRol)
    setOperacionElegida('')
    setPreview(null)
  }

  // Al elegir operación se proponen código y nombre; el usuario puede
  // sobrescribirlos, y a partir de ahí no se vuelven a tocar solos.
  const elegirOperacion = (nueva: TipoMetrica) => {
    setOperacionElegida(nueva)
    setPreview(null)

    const info = operaciones.find((o) => o.codigo === nueva)
    if (!campo || !info) return

    setCodigo(sugerirCodigo(campo.codigo, info.sufijoCodigo, (data?.metricas ?? []).map((m) => m.codigo)))
    setNombre(sugerirNombre(campo, info))
    setTopN(info.exigeTopN ? String(TOP_N_POR_DEFECTO) : '')
    setModoResultado('UNICO')
  }

  const esDistribucion = operacionElegida === 'DISTRIBUCION'
  const admiteAgrupacionOSerie = operacion?.planResultado === 'UNICO' && !esDistribucion

  const opcionesVisualizacion = useMemo(() => {
    if (!operacion) return []
    if (esDistribucion) {
      return visualizacionesDeDistribucion(
        campo?.valoresDistintos ?? 0,
        topN === '' ? null : Number(topN),
        data?.perfil.maxCategoriasDonut ?? 8,
      )
    }
    if (modoResultado === 'AGRUPADO') return visualizacionesSegunPlan('AGRUPADO')
    if (modoResultado === 'SERIE') return visualizacionesSegunPlan('SERIE')
    return visualizacionesSegunPlan('UNICO')
  }, [operacion, esDistribucion, campo, topN, modoResultado, data])

  // Si cambian las opciones, la visualización elegida puede dejar de existir.
  useEffect(() => {
    if (!opcionesVisualizacion.some((o) => o.valor === visualizacion)) {
      setVisualizacion(opcionesVisualizacion[0]?.valor ?? '')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opcionesVisualizacion])

  const construirPayload = (): MetricaClinicaRequestDto => {
    const campos = data?.metadata.campos ?? []
    const operadores = data?.catalogo.operadoresFiltro ?? []

    return {
      codigo: codigo.trim(),
      nombre: nombre.trim(),
      descripcion: descripcion.trim() || null,
      tipoMetrica: operacionElegida as TipoMetrica,
      configuracion: construirConfiguracionDesdeColumna({
        operacion: operacionElegida as TipoMetrica,
        codigoCampo,
        filtros: filtrosParaPayload(filtros, campos, operadores),
        incluirSinDato,
        topN: topN === '' ? null : Number(topN),
      }),
      unidad: null,
      decimales: decimales === '' ? null : Number(decimales),
      orden: null,
    }
  }

  const validar = (): Record<string, string> => {
    const nuevos: Record<string, string> = {}
    if (!codigoCampo) nuevos.campo = 'Elige la columna que quieres medir.'
    if (!operacionElegida) nuevos.operacion = 'Elige qué quieres calcular.'
    if (!codigo.trim()) nuevos.codigo = 'El código es obligatorio.'
    if (!nombre.trim()) nuevos.nombre = 'El nombre es obligatorio.'

    if (operacion?.exigeTopN && (topN === '' || Number(topN) <= 0)) {
      nuevos.topN = 'Indica cuántas categorías conservar (un número mayor que cero).'
    }
    if (modoResultado === 'AGRUPADO' && !campoAgrupacionWidget) {
      nuevos.agrupacion = 'Elige por qué campo agrupar.'
    }
    if (modoResultado === 'SERIE' && !granularidadWidget) {
      nuevos.granularidad = 'Elige cada cuánto tiempo agrupar.'
    }

    const codigosUsados = new Set((data?.metricas ?? []).map((m) => m.codigo.toLowerCase()))
    if (codigo.trim() && codigosUsados.has(codigo.trim().toLowerCase())) {
      nuevos.codigo = 'Ya existe una métrica con este código en el dataset. Cambia el código.'
    }

    return nuevos
  }

  const previsualizar = async () => {
    setErrorBackend(null)
    setPreview(null)
    const nuevos = validar()
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      setPreview(await previewMetrica(datasetId ?? '', { metrica: construirPayload() }))
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'No se pudo previsualizar.')
    }
  }

  /**
   * Crea la métrica y muestra el estado de éxito con el paso siguiente.
   *
   * `conWidget` solo decide si el formulario rápido aparece ya desplegado: la
   * métrica se guarda igual en ambos casos, y el widget nunca se crea a
   * espaldas del usuario.
   */
  const guardar = async (conWidget: boolean) => {
    setErrorBackend(null)
    const nuevos = validar()
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    setGuardando(true)
    try {
      const metrica = await crearMetrica(datasetId ?? '', construirPayload())
      setMetricaCreada(metrica)
      setAbrirFormularioWidget(conWidget)
      setGuardando(false)
      // Subir arriba: el estado de éxito se pinta al principio y el usuario
      // está al final de un formulario de siete pasos.
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'No se pudo crear la métrica.')
      setGuardando(false)
    }
  }

  /** Vuelve a empezar sin salir de la pantalla. */
  const crearOtra = () => {
    setMetricaCreada(null)
    setCodigoCampo('')
    setOperacionElegida('')
    setFiltros([])
    setPreview(null)
    setErrores({})
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

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
                { label: data.perfil.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Métricas', to: `/datasets/${datasetId}/metricas` },
                { label: 'Nueva métrica', to: `/datasets/${datasetId}/metricas/nueva` },
                { label: 'Desde una columna' },
              ]}
            />
            <h1>Crear métrica desde una columna</h1>
            <p className={styles.intro}>
              Elige una columna de <strong>{data.perfil.dataset.nombre}</strong> y te propondremos qué se puede
              medir con ella. {data.perfil.totalRegistros} registros importados.
            </p>

            <ErrorBanner mensaje={errorBackend} />

            {/* La métrica ya está guardada: a partir de aquí lo único que queda
                por decidir es si se ve en algún dashboard. */}
            {metricaCreada && (
              <MetricaCreadaPanel
                datasetId={datasetId ?? ''}
                metrica={metricaCreada}
                campos={data.metadata.campos}
                paneles={data.paneles}
                granularidades={data.catalogo.granularidades}
                panelOrigenId={panelOrigenId}
                abrirFormulario={abrirFormularioWidget}
                inicial={{
                  forma:
                    modoResultado === 'AGRUPADO'
                      ? 'COMPARATIVA'
                      : modoResultado === 'SERIE'
                        ? 'SERIE_TEMPORAL'
                        : 'ACTUAL',
                  visualizacion,
                  campoAgrupacion: campoAgrupacionWidget,
                  granularidad: granularidadWidget,
                  campoFecha: campoFechaWidget,
                }}
                onCrearOtra={crearOtra}
                rutaCatalogo={`/datasets/${datasetId}/metricas`}
              />
            )}

            {/* ---------- Paso 1: columna ---------- */}
            <Card title="Paso 1 — ¿Qué columna quieres medir?">
              <SelectorCampoPerfil
                campos={data.perfil.campos}
                seleccionado={codigoCampo}
                onSeleccionar={elegirCampo}
                error={errores.campo}
              />
            </Card>

            {campo && (
              <>
                {/* ---------- Paso 2: interpretación ---------- */}
                <Card title="Paso 2 — ¿Cómo hay que interpretarla?">
                  <p className={styles.ayuda}>
                    Hemos deducido que esta columna es <strong>{ETIQUETA_ROL[campo.rolSugerido] ?? campo.rolSugerido}</strong>{' '}
                    a partir de su tipo de dato y de sus {campo.valoresDistintos} valores distintos.
                    {campo.rolesAlternativos.length === 0
                      ? ' Su tipo de dato no admite otra interpretación.'
                      : ' Puedes corregirlo si no es lo que esperabas.'}
                  </p>
                  {campo.rolesAlternativos.length > 0 ? (
                    <div className={styles.opciones} role="radiogroup" aria-label="Interpretación de la columna">
                      {[campo.rolSugerido, ...campo.rolesAlternativos].map((rol) => (
                        <label key={rol} className={styles.opcion}>
                          <input
                            type="radio"
                            name="rol"
                            checked={rolElegido === rol}
                            onChange={() => cambiarRol(rol)}
                          />
                          {ETIQUETA_ROL[rol] ?? rol}
                          {rol === campo.rolSugerido && <span className={styles.sugerido}>sugerido</span>}
                        </label>
                      ))}
                    </div>
                  ) : (
                    <p className={styles.rolFijo}>{ETIQUETA_ROL[campo.rolSugerido] ?? campo.rolSugerido}</p>
                  )}
                </Card>

                {/* ---------- Paso 3: operación ---------- */}
                <Card title="Paso 3 — ¿Qué quieres saber de esta columna?">
                  {errores.operacion && <p className={styles.error}>{errores.operacion}</p>}
                  <ul className={styles.operaciones}>
                    {operaciones.map((op) => (
                      <li key={op.codigo}>
                        <label
                          className={`${styles.operacion} ${
                            operacionElegida === op.codigo ? styles.operacionActiva : ''
                          }`}
                        >
                          <input
                            type="radio"
                            name="operacion"
                            checked={operacionElegida === op.codigo}
                            onChange={() => elegirOperacion(op.codigo)}
                          />
                          <span>
                            <span className={styles.operacionNombre}>{op.nombre}</span>
                            <span className={styles.operacionExplicacion}>{op.explicacion}</span>
                            {op.advertencia && <span className={styles.advertencia}>{op.advertencia}</span>}
                          </span>
                        </label>
                      </li>
                    ))}
                  </ul>
                </Card>
              </>
            )}

            {campo && operacion && (
              <>
                {/* ---------- Paso 4: filtros y base ---------- */}
                <Card title="Paso 4 — Filtros y base de cálculo (opcional)">
                  <p className={styles.ayuda}>
                    Los filtros acotan la población sobre la que se calcula. Sin filtros, se usan los{' '}
                    {data.perfil.totalRegistros} registros del dataset.
                  </p>
                  <FiltroBuilder
                    filtros={filtros}
                    onChange={setFiltros}
                    campos={data.metadata.campos}
                    operadoresCatalogo={data.catalogo.operadoresFiltro}
                  />

                  {(esDistribucion || operacionElegida === 'CATEGORIA_PRINCIPAL') && (
                    <FormField
                      label="Registros sin dato"
                      help="En clínica, que un dato no conste no significa que la respuesta sea «no». Por eso «Sin dato» se muestra como categoría propia salvo que decidas excluirlo."
                    >
                      <select
                        value={incluirSinDato ? 'incluir' : 'excluir'}
                        onChange={(e) => setIncluirSinDato(e.target.value === 'incluir')}
                      >
                        <option value="incluir">Mostrarlos como «Sin dato»</option>
                        <option value="excluir">Excluirlos del cálculo</option>
                      </select>
                    </FormField>
                  )}

                  {esDistribucion && (
                    <FormField
                      label="Número máximo de categorías"
                      help={`Esta columna tiene ${campo.valoresDistintos} valores distintos. Las menos frecuentes se agrupan en «Otros», que es solo una forma de presentarlas: no es un valor sobre el que se pueda filtrar. Déjalo vacío para mostrarlas todas.`}
                      error={errores.topN}
                    >
                      <input
                        type="number"
                        min={1}
                        step={1}
                        value={topN}
                        onChange={(e) => setTopN(e.target.value)}
                        placeholder="todas"
                      />
                    </FormField>
                  )}
                </Card>

                {/* ---------- Paso 5: agrupación o periodo ---------- */}
                {admiteAgrupacionOSerie && (
                  <Card title="Paso 5 — ¿Un valor único, desglosado o a lo largo del tiempo?">
                    <div className={styles.opciones} role="radiogroup" aria-label="Forma del resultado">
                      <label className={styles.opcion}>
                        <input
                          type="radio"
                          name="modoResultado"
                          checked={modoResultado === 'UNICO'}
                          onChange={() => setModoResultado('UNICO')}
                        />
                        Un solo valor
                      </label>
                      <label className={styles.opcion}>
                        <input
                          type="radio"
                          name="modoResultado"
                          checked={modoResultado === 'AGRUPADO'}
                          onChange={() => setModoResultado('AGRUPADO')}
                        />
                        Agrupado por categoría
                      </label>
                      <label className={styles.opcion}>
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
                      <FormField
                        label="Agrupar por"
                        help="El cálculo se repetirá una vez por cada valor de este campo."
                        error={errores.agrupacion}
                      >
                        <select
                          value={campoAgrupacionWidget}
                          onChange={(e) => setCampoAgrupacionWidget(e.target.value)}
                        >
                          <option value="">— seleccionar campo —</option>
                          {camposAgrupables
                            .filter((c) => c.codigo !== data.perfil.campoIndividuo)
                            .map((c) => (
                              <option key={c.codigo} value={c.codigo}>
                                {c.etiqueta}
                              </option>
                            ))}
                        </select>
                      </FormField>
                    )}

                    {modoResultado === 'SERIE' && (
                      <div className={styles.grid}>
                        <FormField label="Cada cuánto tiempo" error={errores.granularidad}>
                          <select
                            value={granularidadWidget}
                            onChange={(e) => setGranularidadWidget(e.target.value)}
                          >
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
                      </div>
                    )}

                    {modoResultado !== 'UNICO' && (
                      <p className={styles.ayuda}>
                        La vista previa muestra el valor sin desglosar. El desglose se aplica al widget y estará
                        disponible en el dashboard.
                      </p>
                    )}
                  </Card>
                )}

                {/* ---------- Paso 6: visualización ---------- */}
                <Card title="Paso 6 — Cómo se verá">
                  {opcionesVisualizacion.length > 1 ? (
                    <div className={styles.opciones} role="radiogroup" aria-label="Visualización">
                      {opcionesVisualizacion.map((o) => (
                        <label key={o.valor} className={styles.opcion}>
                          <input
                            type="radio"
                            name="visualizacion"
                            checked={visualizacion === o.valor}
                            onChange={() => setVisualizacion(o.valor)}
                          />
                          {o.etiqueta}
                        </label>
                      ))}
                    </div>
                  ) : (
                    // Un desplegable con una sola opción aparenta una decisión
                    // que no existe: se enuncia y punto.
                    <p className={styles.rolFijo}>{opcionesVisualizacion[0]?.etiqueta ?? '—'}</p>
                  )}
                </Card>

                {/* ---------- Paso 7: identidad y guardado ---------- */}
                <Card title="Paso 7 — Nombre y guardado">
                  <div className={styles.resumen}>
                    <p className={styles.resumenTitulo}>Cómo se calcula</p>
                    <p className={styles.resumenTexto}>{describirCalculo(campo, operacion, filtros.length)}</p>
                  </div>

                  <div className={styles.grid}>
                    <FormField label="Nombre" error={errores.nombre}>
                      <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
                    </FormField>
                    <FormField
                      label="Código interno"
                      help="Identificador estable dentro del dataset. Se propone uno legible y libre."
                      error={errores.codigo}
                    >
                      <input value={codigo} onChange={(e) => setCodigo(e.target.value)} />
                    </FormField>
                    <FormField label="Decimales">
                      <input
                        type="number"
                        min={0}
                        step={1}
                        value={decimales}
                        onChange={(e) => setDecimales(e.target.value)}
                        placeholder="2"
                      />
                    </FormField>
                    <div className={styles.anchoCompleto}>
                      <FormField label="Descripción (opcional)">
                        <textarea rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
                      </FormField>
                    </div>
                  </div>

                  <div className={styles.botones}>
                    <button type="button" className="btn btnPrimary" disabled={guardando} onClick={() => guardar(false)}>
                      {guardando ? 'Guardando…' : 'Crear métrica'}
                    </button>
                    <button
                      type="button"
                      className="btn btnPrimary"
                      disabled={guardando}
                      onClick={() => guardar(true)}
                    >
                      Crear y añadir al dashboard
                    </button>
                    <button type="button" className="btn btnSecondary" onClick={previsualizar}>
                      Previsualizar
                    </button>
                    <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas/nueva`}>
                      Cancelar
                    </Link>
                  </div>
                </Card>

                {preview && (
                  <Card title="Vista previa" subtitle="Calculada sobre los datos actuales; no se ha guardado nada.">
                    <WidgetActual resultado={preview} />
                  </Card>
                )}
              </>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
