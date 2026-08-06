import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import type {
  CampoMetricaMetadataDto,
  ConfiguracionWidgetDto,
  Granularidad,
  MetricaClinicaResponseDto,
  OpcionCatalogoDto,
  PanelClinicoResponseDto,
  PanelMetricaResponseDto,
  TipoVisualizacion,
} from '../../api/types'
import { actualizarConfiguracionWidget, anadirWidget, crearPanel, listarWidgets } from '../../api/panelesApi'
import { Card } from '../Card'
import { FormField } from '../FormField'
import { ErrorBanner } from '../ErrorBanner'
import {
  ETIQUETA_FORMA,
  anchoRecomendado,
  describirMetrica,
  formasCompatibles,
  panelPrincipal,
  visualizacionesDeForma,
} from './widgetRapido'
import type { FormaResultado } from './widgetRapido'
import styles from './MetricaCreadaPanel.module.css'

const ANCHOS = [
  { valor: '3', etiqueta: 'Pequeño (un cuarto de fila)' },
  { valor: '6', etiqueta: 'Medio (media fila)' },
  { valor: '12', etiqueta: 'Ancho completo' },
]

interface MetricaCreadaPanelProps {
  datasetId: string
  metrica: MetricaClinicaResponseDto
  campos: CampoMetricaMetadataDto[]
  paneles: PanelClinicoResponseDto[]
  granularidades: OpcionCatalogoDto[]
  /** Panel desde el que se inició la creación, si la hubo. */
  panelOrigenId?: string | null
  /**
   * Abre el formulario ya desplegado. Lo usa el botón "Crear y añadir al
   * dashboard": el usuario ya dijo que lo quiere, no hay que volver a
   * preguntárselo.
   */
  abrirFormulario?: boolean
  /**
   * Lo que el usuario eligió en el asistente (forma, visualización,
   * agrupación…). Se respeta como valor inicial para no perder decisiones que
   * ya había tomado; si no viene nada, se calcula lo recomendado.
   */
  inicial?: {
    forma?: FormaResultado
    visualizacion?: string
    campoAgrupacion?: string
    granularidad?: string
    campoFecha?: string
  }
  /** Volver a empezar: el contenedor decide si limpia el formulario o recarga. */
  onCrearOtra: () => void
  /** Dónde va "Ver métrica" y a dónde se vuelve al terminar. */
  rutaCatalogo: string
}

type Fase = 'exito' | 'widgetCreado'

/**
 * Qué ocurre justo después de crear una métrica (Fase 6.9I.4).
 *
 * <p>Antes, guardar una métrica llevaba al listado sin más: el usuario acababa
 * de decidir qué quería medir y se le devolvía a una tabla, con el widget a
 * tres pantallas de distancia. Aquí se le ofrece el paso natural siguiente ya
 * preparado —dashboard, visualización, título y tamaño preseleccionados—, sin
 * obligarle a tomarlo.
 *
 * <p>La métrica ya está guardada cuando esto aparece: cancelar aquí no la
 * pierde, y un fallo al crear el widget tampoco la borra.
 */
export function MetricaCreadaPanel({
  datasetId,
  metrica,
  campos,
  paneles,
  granularidades,
  panelOrigenId,
  abrirFormulario = false,
  inicial,
  onCrearOtra,
  rutaCatalogo,
}: MetricaCreadaPanelProps) {
  const navigate = useNavigate()

  const resumen = useMemo(() => describirMetrica(metrica, campos), [metrica, campos])
  const formas = useMemo(() => formasCompatibles(metrica, campos), [metrica, campos])

  const [fase, setFase] = useState<Fase>('exito')
  const [mostrarFormulario, setMostrarFormulario] = useState(abrirFormulario)

  // --- Estado del formulario rápido ---
  const [panelId, setPanelId] = useState('')
  const [forma, setForma] = useState<FormaResultado>(
    inicial?.forma && formas.includes(inicial.forma) ? inicial.forma : formas[0],
  )
  const [visualizacion, setVisualizacion] = useState(inicial?.visualizacion ?? '')
  const [titulo, setTitulo] = useState(metrica.nombre)
  const [ancho, setAncho] = useState('3')
  const [campoAgrupacion, setCampoAgrupacion] = useState(inicial?.campoAgrupacion ?? '')
  const [granularidad, setGranularidad] = useState(inicial?.granularidad || 'MES')
  const [campoFecha, setCampoFecha] = useState(inicial?.campoFecha ?? '')
  const [campoSegmentacion, setCampoSegmentacion] = useState('')

  // --- Crear panel sobre la marcha ---
  const [nombrePanel, setNombrePanel] = useState('Dashboard principal')
  const [codigoPanel, setCodigoPanel] = useState('dashboard_principal')
  const [descripcionPanel, setDescripcionPanel] = useState('')

  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [panelDestinoId, setPanelDestinoId] = useState<number | null>(null)
  const [panelConDuplicado, setPanelConDuplicado] = useState<number | null>(null)

  const sinPaneles = paneles.length === 0

  // Preselección: el panel de origen manda; si no lo hay, el principal.
  useEffect(() => {
    if (panelId !== '' || sinPaneles) return
    const origen = panelOrigenId ? paneles.find((p) => String(p.id) === String(panelOrigenId)) : null
    const elegido = origen ?? panelPrincipal(paneles)
    if (elegido) setPanelId(String(elegido.id))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paneles, panelOrigenId])

  const opcionesVisualizacion = useMemo(
    () => visualizacionesDeForma(metrica, forma),
    [metrica, forma],
  )

  // Al cambiar la forma, la visualización y el ancho se recolocan solos en lo
  // recomendado; el usuario puede cambiarlos después. Si la visualización
  // actual sigue siendo válida (por ejemplo la que ya eligió en el asistente),
  // se respeta en vez de reemplazarla.
  useEffect(() => {
    const sigueValiendo = opcionesVisualizacion.some((o) => o.valor === visualizacion)
    const elegida = sigueValiendo ? visualizacion : (opcionesVisualizacion[0]?.valor ?? '')
    setVisualizacion(elegida)
    setAncho(String(anchoRecomendado(elegida, forma)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [forma])

  useEffect(() => {
    if (visualizacion) setAncho(String(anchoRecomendado(visualizacion, forma)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visualizacion])

  const camposAgrupables = campos.filter((c) => c.utilizableComoCampoAgrupacion)
  const camposFecha = campos.filter((c) => c.utilizableComoCampoFecha)

  /** ¿Esta métrica ya tiene un widget en el panel elegido? */
  const comprobarDuplicado = async (idPanel: string): Promise<PanelMetricaResponseDto | null> => {
    try {
      const widgets = await listarWidgets(idPanel)
      return widgets.find((w) => w.metricaId === metrica.id) ?? null
    } catch {
      // Si no se puede comprobar, no se bloquea: el aviso es una cortesía, no
      // una restricción (una métrica puede estar legítimamente en dos paneles).
      return null
    }
  }

  const validar = (): Record<string, string> => {
    const e: Record<string, string> = {}
    if (!sinPaneles && !panelId) e.panel = 'Elige a qué dashboard añadirlo.'
    if (!visualizacion) e.visualizacion = 'Elige cómo se verá.'
    if (forma === 'COMPARATIVA' && !campoAgrupacion) e.agrupacion = 'Elige por qué campo agrupar.'
    if (forma === 'SERIE_TEMPORAL' && !granularidad) e.granularidad = 'Elige cada cuánto tiempo agrupar.'
    if (sinPaneles && !nombrePanel.trim()) e.nombrePanel = 'El nombre del dashboard es obligatorio.'
    if (sinPaneles && !codigoPanel.trim()) e.codigoPanel = 'El código del dashboard es obligatorio.'
    return e
  }

  const anadir = async () => {
    setErrorBackend(null)
    setAviso(null)
    const e = validar()
    setErrores(e)
    if (Object.keys(e).length > 0) return

    setGuardando(true)
    try {
      // 1. El dashboard: el existente, o uno nuevo creado aquí mismo.
      let idPanel = panelId
      if (sinPaneles) {
        const panel = await crearPanel(datasetId, {
          codigo: codigoPanel.trim(),
          nombre: nombrePanel.trim(),
          descripcion: descripcionPanel.trim() || null,
          orden: 0,
        })
        idPanel = String(panel.id)
      }

      // 2. Duplicado: se avisa y se deja decidir, en vez de crear dos widgets
      // iguales sin decir nada. Una métrica en dos paneles sí es legítima.
      const yaEsta = await comprobarDuplicado(idPanel)
      if (yaEsta) {
        // Una métrica puede estar en VARIOS dashboards, pero no dos veces en el
        // mismo: el backend lo rechaza. Se dice antes de intentarlo, en vez de
        // dejar que el usuario choque contra el error.
        setPanelConDuplicado(Number(idPanel))
        setAviso(
          `«${metrica.nombre}» ya está en este dashboard como «${
            yaEsta.tituloPersonalizado ?? yaEsta.metricaNombre
          }». Puedes verla ahí, o añadirla a otro dashboard distinto.`,
        )
        setGuardando(false)
        return
      }

      await crearWidget(idPanel)
    } catch (err) {
      setErrorBackend(
        err instanceof Error
          ? `No se pudo añadir el widget. La métrica sí se ha guardado. ${err.message}`
          : 'No se pudo añadir el widget. La métrica sí se ha guardado.',
      )
      setGuardando(false)
    }
  }

  const crearWidget = async (idPanel: string) => {
    const widget = await anadirWidget(idPanel, {
      metricaId: metrica.id,
      tipoVisualizacion: visualizacion as TipoVisualizacion,
      tituloPersonalizado: titulo.trim() && titulo.trim() !== metrica.nombre ? titulo.trim() : null,
      descripcionPersonalizada: null,
      ancho: Number(ancho),
      orden: null,
    })

    // La forma del resultado se configura aparte porque vive en el widget, no
    // en la métrica: la misma métrica puede verse como valor, agrupada o en el
    // tiempo en tres paneles distintos.
    if (forma !== 'ACTUAL') {
      const configuracionWidget: ConfiguracionWidgetDto =
        forma === 'COMPARATIVA'
          ? { campoAgrupacion, granularidad: null, campoFecha: null, campoSegmentacion: null }
          : {
              granularidad: granularidad as Granularidad,
              campoFecha: campoFecha || null,
              campoSegmentacion: campoSegmentacion || null,
              campoAgrupacion: null,
            }

      await actualizarConfiguracionWidget(idPanel, widget.id, {
        tipoResultado: forma,
        configuracionWidget,
      })
    }

    setPanelDestinoId(Number(idPanel))
    setFase('widgetCreado')
    setGuardando(false)
  }

  // ------------------------------------------------------------------
  // Widget ya creado
  // ------------------------------------------------------------------

  if (fase === 'widgetCreado') {
    return (
      <Card title="Widget añadido al dashboard" className={styles.tarjetaExito}>
        <p className={styles.mensajeExito}>
          «{titulo.trim() || metrica.nombre}» ya se ve en el dashboard.
        </p>
        <div className={styles.acciones}>
          <Link className="btn btnPrimary" to={`/paneles/${panelDestinoId}/dashboard`}>
            Abrir dashboard
          </Link>
          <button type="button" className="btn btnSecondary" onClick={onCrearOtra}>
            Seguir creando métricas
          </button>
          <Link className="btn btnSecondary" to={rutaCatalogo}>
            Ver todas las métricas
          </Link>
        </div>
      </Card>
    )
  }

  // ------------------------------------------------------------------
  // Métrica creada: éxito + formulario rápido
  // ------------------------------------------------------------------

  return (
    <Card title="Métrica creada correctamente" className={styles.tarjetaExito}>
      <dl className={styles.resumen}>
        <div>
          <dt>Nombre</dt>
          <dd>{metrica.nombre}</dd>
        </div>
        <div>
          <dt>Operación</dt>
          <dd>{resumen.operacion}</dd>
        </div>
        {resumen.campo && (
          <div>
            <dt>Campo analizado</dt>
            <dd>{resumen.campo}</dd>
          </div>
        )}
        <div>
          <dt>Resultado</dt>
          <dd>{resumen.forma}</dd>
        </div>
        <div>
          <dt>Visualización recomendada</dt>
          <dd>{visualizacionesDeForma(metrica, formas[0])[0]?.etiqueta ?? '—'}</dd>
        </div>
      </dl>

      <ErrorBanner mensaje={errorBackend} />

      {!mostrarFormulario ? (
        <>
          <p className={styles.pregunta}>
            {sinPaneles
              ? 'Este dataset todavía no tiene dashboard. Puedes crearlo ahora y añadir esta métrica en el mismo paso.'
              : '¿Quieres verla ya en un dashboard?'}
          </p>
          <div className={styles.acciones}>
            <button type="button" className="btn btnPrimary" onClick={() => setMostrarFormulario(true)}>
              {sinPaneles ? 'Crear dashboard y añadir widget' : 'Añadir al dashboard'}
            </button>
            <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas/${metrica.id}/editar`}>
              Ver métrica
            </Link>
            <button type="button" className="btn btnSecondary" onClick={() => navigate(rutaCatalogo)}>
              {sinPaneles ? 'Guardar solo la métrica' : 'Guardar sin añadir'}
            </button>
            <button type="button" className="btn btnSecondary" onClick={onCrearOtra}>
              Crear otra métrica
            </button>
          </div>
        </>
      ) : (
        <div className={styles.formulario}>
          {aviso && (
            <div className={styles.aviso}>
              <p>{aviso}</p>
              <div className={styles.acciones}>
                {panelConDuplicado && (
                  <Link className="btn btnSecondary" to={`/paneles/${panelConDuplicado}/dashboard`}>
                    Ver dónde está
                  </Link>
                )}
                <button type="button" className="btn btnSecondary" onClick={() => setAviso(null)}>
                  Elegir otro dashboard
                </button>
              </div>
            </div>
          )}

          {sinPaneles ? (
            <div className={styles.grid}>
              <FormField label="Nombre del dashboard" error={errores.nombrePanel}>
                <input value={nombrePanel} onChange={(e) => setNombrePanel(e.target.value)} />
              </FormField>
              <FormField
                label="Código interno"
                help="Identificador estable del dashboard dentro del dataset."
                error={errores.codigoPanel}
              >
                <input value={codigoPanel} onChange={(e) => setCodigoPanel(e.target.value)} />
              </FormField>
              <div className={styles.anchoCompleto}>
                <FormField label="Descripción (opcional)">
                  <input value={descripcionPanel} onChange={(e) => setDescripcionPanel(e.target.value)} />
                </FormField>
              </div>
            </div>
          ) : (
            <FormField label="Dashboard" error={errores.panel}>
              <select value={panelId} onChange={(e) => setPanelId(e.target.value)}>
                {paneles.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nombre}
                    {panelOrigenId && String(p.id) === String(panelOrigenId) ? ' (desde donde venías)' : ''}
                  </option>
                ))}
              </select>
            </FormField>
          )}

          {formas.length > 1 && (
            <FormField label="Cómo se calcula">
              <select value={forma} onChange={(e) => setForma(e.target.value as FormaResultado)}>
                {formas.map((f) => (
                  <option key={f} value={f}>
                    {ETIQUETA_FORMA[f]}
                  </option>
                ))}
              </select>
            </FormField>
          )}

          {forma === 'COMPARATIVA' && (
            <FormField label="Agrupar por" error={errores.agrupacion}>
              <select value={campoAgrupacion} onChange={(e) => setCampoAgrupacion(e.target.value)}>
                <option value="">— seleccionar campo —</option>
                {camposAgrupables.map((c) => (
                  <option key={c.codigo} value={c.codigo}>
                    {c.etiqueta}
                  </option>
                ))}
              </select>
            </FormField>
          )}

          {forma === 'SERIE_TEMPORAL' && (
            <div className={styles.grid}>
              <FormField label="Cada cuánto tiempo" error={errores.granularidad}>
                <select value={granularidad} onChange={(e) => setGranularidad(e.target.value)}>
                  {granularidades.map((g) => (
                    <option key={g.codigo} value={g.codigo}>
                      {g.nombre}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Campo de fecha (opcional)">
                <select value={campoFecha} onChange={(e) => setCampoFecha(e.target.value)}>
                  <option value="">— por defecto —</option>
                  {camposFecha.map((c) => (
                    <option key={c.codigo} value={c.codigo}>
                      {c.etiqueta}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Separar series por (opcional)">
                <select value={campoSegmentacion} onChange={(e) => setCampoSegmentacion(e.target.value)}>
                  <option value="">— sin separar —</option>
                  {camposAgrupables.map((c) => (
                    <option key={c.codigo} value={c.codigo}>
                      {c.etiqueta}
                    </option>
                  ))}
                </select>
              </FormField>
            </div>
          )}

          <div className={styles.grid}>
            {opcionesVisualizacion.length > 1 ? (
              <FormField label="Cómo se verá" error={errores.visualizacion}>
                <select value={visualizacion} onChange={(e) => setVisualizacion(e.target.value)}>
                  {opcionesVisualizacion.map((o) => (
                    <option key={o.valor} value={o.valor}>
                      {o.etiqueta}
                    </option>
                  ))}
                </select>
              </FormField>
            ) : (
              <FormField label="Cómo se verá">
                {/* Una sola opción real: se enuncia en vez de fingir una
                    elección con un desplegable de un único elemento. */}
                <p className={styles.valorFijo}>{opcionesVisualizacion[0]?.etiqueta ?? '—'}</p>
              </FormField>
            )}
            <FormField label="Título en el dashboard">
              <input value={titulo} onChange={(e) => setTitulo(e.target.value)} />
            </FormField>
            <FormField label="Tamaño">
              <select value={ancho} onChange={(e) => setAncho(e.target.value)}>
                {ANCHOS.map((a) => (
                  <option key={a.valor} value={a.valor}>
                    {a.etiqueta}
                  </option>
                ))}
              </select>
            </FormField>
          </div>

          <div className={styles.acciones}>
            <button type="button" className="btn btnPrimary" disabled={guardando} onClick={anadir}>
              {guardando ? 'Añadiendo…' : sinPaneles ? 'Crear dashboard y añadir widget' : 'Añadir widget'}
            </button>
            <button type="button" className="btn btnSecondary" onClick={() => setMostrarFormulario(false)}>
              Cancelar
            </button>
          </div>
          <p className={styles.notaCancelar}>
            La métrica ya está guardada: cancelar aquí no la pierde.
          </p>
        </div>
      )}
    </Card>
  )
}
