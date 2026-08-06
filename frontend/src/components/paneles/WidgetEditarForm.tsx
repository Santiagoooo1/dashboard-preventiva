import { useEffect, useState } from 'react'
import type {
  ConfiguracionWidgetDto,
  Granularidad,
  OpcionCatalogoDto,
  PanelMetricaConfiguracionWidgetRequestDto,
  PanelMetricaRequestDto,
  PanelMetricaResponseDto,
  TipoResultado,
  TipoVisualizacion,
  TipoVisualizacionCatalogoDto,
  WidgetMetadataDto,
} from '../../api/types'
import { FormField } from '../FormField'
import {
  ETIQUETA_TIPO_RESULTADO,
  ETIQUETA_VISUALIZACION_OFRECIDA,
  normalizarTipoVisualizacion,
} from '../dashboard/visualizacionesCompatibles'
import {
  anchoAlCambiarForma,
  visualizacionAlCambiarForma,
  visualizacionesDeFormaAgregada,
} from '../metrics/widgetRapido'
import type { FormaResultado } from '../metrics/widgetRapido'
import styles from './WidgetForm.module.css'

/** Deja que el backend deduzca la forma a partir de la visualización. */
const AUTOMATICO = '__AUTOMATICO__'

const ANCHOS = [
  { valor: '3', etiqueta: 'Pequeño (un cuarto de fila)' },
  { valor: '6', etiqueta: 'Medio (media fila)' },
  { valor: '12', etiqueta: 'Ancho completo' },
]

interface WidgetEditarFormProps {
  widget: PanelMetricaResponseDto
  /** Metadata del mismo widget: qué formas de resultado admite realmente. */
  meta: WidgetMetadataDto | undefined
  tipoVisualizaciones: TipoVisualizacionCatalogoDto[]
  camposFechaPermitidos: string[]
  camposAgrupacionPermitidos: string[]
  granularidades: OpcionCatalogoDto[]
  /** Abre directamente por la sección de agrupación (acción «Agrupar o segmentar»). */
  enfocarAgrupacion?: boolean
  onGuardar: (
    presentacion: PanelMetricaRequestDto,
    resultado: PanelMetricaConfiguracionWidgetRequestDto | null,
  ) => void
  onCancelar: () => void
  guardando: boolean
}

/**
 * Edición completa de un widget en un solo formulario (Fase 6.9I.4.1).
 *
 * <p>Antes esto estaba partido en dos: «Editar visualización» cambiaba título,
 * gráfico y tamaño, y «Agrupar / segmentar» cambiaba la forma del resultado.
 * Eran dos formularios distintos para el mismo widget, así que cambiar de
 * gráfico y agruparlo a la vez exigía dos vueltas y, sobre todo, no había
 * ninguna pantalla donde se viera el estado completo del widget.
 *
 * <p>Aquí se ve y se cambia todo junto. Se guarda con las dos llamadas que ya
 * existían —presentación y resultado— porque son dos endpoints distintos, pero
 * el usuario solo pulsa una vez.
 */
/** Un único aviso para los dos ajustes automáticos, en lenguaje de usuario. */
function construirAviso(
  visualizacionNueva: string | null,
  anchoSugerido: number,
  anchoAnterior: number,
): string | null {
  const partes: string[] = []

  if (visualizacionNueva) {
    partes.push(
      `Se ha cambiado la visualización a ${ETIQUETA_VISUALIZACION_OFRECIDA[normalizarTipoVisualizacion(visualizacionNueva)]}, porque un indicador de un solo número no puede representar este resultado.`,
    )
  }

  if (anchoSugerido !== anchoAnterior) {
    partes.push(
      `Se ha ampliado el tamaño a ${anchoSugerido === 12 ? 'ancho completo' : 'media fila'} para que quepa.`,
    )
  }

  if (partes.length === 0) return null
  return `${partes.join(' ')} Puedes cambiarlo abajo.`
}

export function WidgetEditarForm({
  widget,
  meta,
  tipoVisualizaciones,
  camposFechaPermitidos,
  camposAgrupacionPermitidos,
  granularidades,
  enfocarAgrupacion = false,
  onGuardar,
  onCancelar,
  guardando,
}: WidgetEditarFormProps) {
  // --- Presentación ---
  const [tipoVisualizacion, setTipoVisualizacion] = useState(widget.tipoVisualizacion)
  const [titulo, setTitulo] = useState(widget.tituloPersonalizado ?? '')
  const [descripcion, setDescripcion] = useState(widget.descripcionPersonalizada ?? '')
  const [ancho, setAncho] = useState(String(widget.ancho ?? 3))
  const [orden, setOrden] = useState(String(widget.orden ?? ''))

  // --- Forma del resultado ---
  const config = meta?.configuracionWidgetActual
  const [tipoResultado, setTipoResultado] = useState(meta?.tipoResultadoWidgetConfigurado ?? AUTOMATICO)
  const [granularidad, setGranularidad] = useState(config?.granularidad ?? '')
  const [campoFecha, setCampoFecha] = useState(config?.campoFecha ?? '')
  const [campoSegmentacion, setCampoSegmentacion] = useState(config?.campoSegmentacion ?? '')
  const [campoAgrupacion, setCampoAgrupacion] = useState(config?.campoAgrupacion ?? '')

  const [errores, setErrores] = useState<Record<string, string>>({})
  const [avisoAncho, setAvisoAncho] = useState<string | null>(null)

  // Solo se ofrecen las formas que el backend admite para esta métrica: para una
  // distribución o una categoría más frecuente, la lista es solo ACTUAL.
  const permitidos = meta?.tipoResultadosPermitidos ?? ['ACTUAL']
  const admiteAgrupar = permitidos.length > 1

  // Con una forma agregada, un KPI no es una opción: ofrecerlo produciría un
  // widget en ERROR. En modo automático se deja el catálogo completo, que es lo
  // que el backend resolverá por su cuenta.
  const visualizacionesOfrecidas =
    tipoResultado === 'COMPARATIVA' || tipoResultado === 'SERIE_TEMPORAL'
      ? tipoVisualizaciones.filter((v) =>
          visualizacionesDeFormaAgregada(tipoResultado).includes(v.codigo as TipoVisualizacion),
        )
      : tipoVisualizaciones

  const esAutomatico = tipoResultado === AUTOMATICO
  const mostrarSerie = tipoResultado === 'SERIE_TEMPORAL'
  const mostrarComparativa = tipoResultado === 'COMPARATIVA'

  // Al abrir desde «Agrupar o segmentar», se lleva el foco a ese control en vez
  // de dejar al usuario buscándolo dentro del formulario.
  useEffect(() => {
    if (!enfocarAgrupacion || !admiteAgrupar) return
    document.getElementById('widget-forma-resultado')?.focus()
  }, [enfocarAgrupacion, admiteAgrupar])

  /**
   * Cambiar la forma del resultado cambia lo que hay que dibujar.
   *
   * <p>Dos ajustes encadenados, en este orden: primero la visualización —un KPI
   * no puede representar una comparativa—, y solo después el ancho, calculado
   * ya con la visualización NUEVA. Hacerlo al revés dejaba un KPI agrupado en
   * ancho 3.
   *
   * <p>Ambos se avisan: el widget cambia de aspecto y el usuario debe saber por
   * qué, sobre todo porque los dos siguen siendo editables aquí mismo.
   */
  const cambiarForma = (nueva: string) => {
    setTipoResultado(nueva)
    setAvisoAncho(null)

    if (nueva === AUTOMATICO) return

    const forma = nueva as FormaResultado
    const visualizacionNueva = visualizacionAlCambiarForma(tipoVisualizacion, forma)
    const cambiaVisualizacion = visualizacionNueva !== tipoVisualizacion

    if (cambiaVisualizacion) setTipoVisualizacion(visualizacionNueva)

    // El ancho se calcula con la visualización que va a quedar, no con la que
    // había. `anchoAlCambiarForma` solo amplía: un ancho mayor puesto a mano se
    // respeta.
    const anchoActual = Number(ancho) || 3
    const sugerido = anchoAlCambiarForma(anchoActual, visualizacionNueva, forma)
    if (sugerido !== anchoActual) setAncho(String(sugerido))

    setAvisoAncho(construirAviso(cambiaVisualizacion ? visualizacionNueva : null, sugerido, anchoActual))
  }

  /**
   * Cambiar la visualización dentro de una forma agregada también cambia el
   * sitio que hace falta: pasar de barras a tabla en una comparativa pide fila
   * completa.
   */
  const cambiarVisualizacion = (nueva: string) => {
    setTipoVisualizacion(nueva)
    setAvisoAncho(null)

    if (esAutomatico || tipoResultado === 'ACTUAL') return

    const anchoActual = Number(ancho) || 3
    const sugerido = anchoAlCambiarForma(anchoActual, nueva, tipoResultado as FormaResultado)
    if (sugerido !== anchoActual) {
      setAncho(String(sugerido))
      setAvisoAncho(construirAviso(null, sugerido, anchoActual))
    }
  }

  const enviar = () => {
    const nuevos: Record<string, string> = {}
    if (!tipoVisualizacion) nuevos.tipoVisualizacion = 'Selecciona cómo se ve el widget.'
    if (mostrarSerie && !granularidad) {
      nuevos.granularidad = 'Para ver la evolución en el tiempo, selecciona cada cuánto agrupar.'
    }
    if (mostrarComparativa && !campoAgrupacion) {
      nuevos.campoAgrupacion = 'Para agrupar el resultado, selecciona un campo.'
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    // El backend resetea orden y ancho si el PUT no los incluye: se reenvían
    // siempre los seis campos.
    const presentacion: PanelMetricaRequestDto = {
      metricaId: widget.metricaId,
      tituloPersonalizado: titulo.trim() || null,
      descripcionPersonalizada: descripcion.trim() || null,
      tipoVisualizacion: tipoVisualizacion as TipoVisualizacion,
      orden: orden === '' ? null : Number(orden),
      ancho: ancho === '' ? null : Number(ancho),
    }

    // Si la métrica no admite agrupar, no se envía configuración de resultado:
    // no hay nada que cambiar y evita un PUT innecesario.
    let resultado: PanelMetricaConfiguracionWidgetRequestDto | null = null
    if (admiteAgrupar) {
      const configuracionWidget: ConfiguracionWidgetDto = {
        granularidad: (granularidad || null) as Granularidad | null,
        campoFecha: campoFecha || null,
        campoSegmentacion: campoSegmentacion || null,
        campoAgrupacion: campoAgrupacion || null,
      }
      resultado = {
        tipoResultado: esAutomatico ? null : (tipoResultado as TipoResultado),
        configuracionWidget,
      }
    }

    onGuardar(presentacion, resultado)
  }

  return (
    <div className={styles.form}>
      <div className={styles.grid}>
        <FormField
          label="Cómo se ve"
          help="Número (indicador), gráfico o tabla."
          error={errores.tipoVisualizacion}
        >
          <select value={tipoVisualizacion} onChange={(e) => cambiarVisualizacion(e.target.value)}>
            {visualizacionesOfrecidas.map((v) => (
              <option key={v.codigo} value={v.codigo}>
                {v.nombre}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Título" help="Vacío = se usa el nombre de la métrica.">
          <input value={titulo} onChange={(e) => setTitulo(e.target.value)} placeholder={widget.metricaNombre} />
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
        <FormField label="Orden" help="Los números más bajos van primero.">
          <input type="number" step={1} value={orden} onChange={(e) => setOrden(e.target.value)} />
        </FormField>
        <FormField label="Descripción">
          <input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
        </FormField>
      </div>

      <div className={styles.seccionResultado}>
        <p className={styles.seccionTitulo}>Cómo se calcula</p>

        {!admiteAgrupar ? (
          // Nunca un control activo sin efecto: si la métrica no admite otra
          // forma, se dice por qué en vez de ofrecer un desplegable inútil.
          <p className={styles.notaBloqueada}>
            Esta métrica no admite agrupación ni evolución en el tiempo: su resultado es un reparto de categorías
            o una etiqueta, no un número que se pueda poner en un eje. Se muestra siempre como{' '}
            <strong>{ETIQUETA_TIPO_RESULTADO[meta?.tipoResultadoActual ?? 'ACTUAL'] ?? 'valor único'}</strong>.
          </p>
        ) : (
          <>
            <FormField
              label="Forma del resultado"
              help="Un valor único, agrupado por una categoría, o su evolución en el tiempo."
            >
              <select
                id="widget-forma-resultado"
                value={tipoResultado}
                onChange={(e) => cambiarForma(e.target.value)}
              >
                <option value={AUTOMATICO}>Automático (según la visualización)</option>
                {permitidos.map((t) => (
                  <option key={t} value={t}>
                    {ETIQUETA_TIPO_RESULTADO[t] ?? t}
                  </option>
                ))}
              </select>
            </FormField>

            {mostrarComparativa && (
              <FormField
                label="Agrupar por"
                help="Ejemplo: ver el resultado desglosado por sexo o por procedimiento."
                error={errores.campoAgrupacion}
              >
                <select value={campoAgrupacion} onChange={(e) => setCampoAgrupacion(e.target.value)}>
                  <option value="">— seleccionar campo —</option>
                  {camposAgrupacionPermitidos.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </FormField>
            )}

            {mostrarSerie && (
              <div className={styles.grid}>
                <FormField label="Cada cuánto tiempo" error={errores.granularidad}>
                  <select value={granularidad} onChange={(e) => setGranularidad(e.target.value)}>
                    <option value="">— seleccionar —</option>
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
                    {camposFechaPermitidos.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField label="Separar series por (opcional)">
                  <select value={campoSegmentacion} onChange={(e) => setCampoSegmentacion(e.target.value)}>
                    <option value="">— sin separar —</option>
                    {camposAgrupacionPermitidos.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </FormField>
              </div>
            )}
          </>
        )}
      </div>

      {avisoAncho && <p className={styles.avisoAncho}>{avisoAncho}</p>}

      <div className={styles.botones}>
        <button type="button" className="btn btnPrimary" disabled={guardando} onClick={enviar}>
          {guardando ? 'Guardando…' : 'Guardar cambios'}
        </button>
        <button type="button" className="btn btnSecondary" onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </div>
  )
}
