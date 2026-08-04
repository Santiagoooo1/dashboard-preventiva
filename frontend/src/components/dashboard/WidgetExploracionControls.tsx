import { useId } from 'react'
import type { CampoFiltroCategoria, CampoIndividuo } from './camposFiltroDashboard'
import type { CapacidadesExploracion, ModoExploracion } from './capacidadesExploracionWidget'
import type { OpcionVisualizacion } from './visualizacionesCompatibles'
import styles from './WidgetExploracionControls.module.css'

interface WidgetExploracionControlsProps {
  capacidades: CapacidadesExploracion
  modo: ModoExploracion
  campoAgrupacion: string
  valorIndividuo: string
  campoIndividuo: CampoIndividuo | null
  opcionesVisualizacion: OpcionVisualizacion[]
  visualizacionActual: string
  ocupado: boolean
  /** Los KPI usan una versión más contenida: el número debe seguir mandando. */
  compacto?: boolean
  /**
   * Individuo ya fijado por el contexto global. Si existe, el modo Paciente
   * local no se ofrece: elegir otro produciría "H003 AND H002", siempre vacío.
   */
  individuoGlobal?: string | null
  onModo: (modo: ModoExploracion) => void
  onCampoAgrupacion: (codigo: string) => void
  onValorIndividuo: (valor: string) => void
  onVisualizacion: (valor: string) => void
}

export function WidgetExploracionControls({
  capacidades,
  modo,
  campoAgrupacion,
  valorIndividuo,
  campoIndividuo,
  opcionesVisualizacion,
  visualizacionActual,
  ocupado,
  compacto = false,
  individuoGlobal = null,
  onModo,
  onCampoAgrupacion,
  onValorIndividuo,
  onVisualizacion,
}: WidgetExploracionControlsProps) {
  const id = useId()
  const { soportaCategoria, camposAgrupables } = capacidades

  // Con el contexto global ya limitado a un individuo, el modo local sobra.
  const hayIndividuoGlobal = Boolean(individuoGlobal)
  const soportaIndividuo = capacidades.soportaIndividuo && !hayIndividuoGlobal

  const puedeAnalizar = soportaCategoria || soportaIndividuo
  // `opcionesVisualizacion` YA llega normalizada por el renderer (fuente
  // única). Un selector con una sola opción no es una decisión: es ruido.
  const puedeElegirVista = opcionesVisualizacion.length > 1

  if (!puedeAnalizar && !puedeElegirVista) return null

  // Solo se ofrecen los modos realmente soportados por este widget.
  // En un KPI, "Desglosar" describe mejor lo que ocurre (un valor único pasa a
  // varios resultados) que "Categoría", que sugiere una agrupación ya
  // existente. El estado interno sigue siendo CATEGORIA en ambos casos.
  const modos: { valor: ModoExploracion; etiqueta: string; titulo: string }[] = [
    { valor: 'ACTUAL', etiqueta: 'Actual', titulo: 'Vista guardada de este indicador' },
    ...(soportaCategoria
      ? [
          {
            valor: 'CATEGORIA' as const,
            etiqueta: compacto ? 'Desglosar' : 'Categoría',
            titulo: 'Desglosa este indicador por un campo',
          },
        ]
      : []),
    ...(soportaIndividuo
      ? [
          {
            valor: 'INDIVIDUO' as const,
            etiqueta: campoIndividuo ? 'Paciente' : 'Individuo',
            titulo: 'Calcula este indicador solo para un individuo',
          },
        ]
      : []),
  ]

  const exploracionActiva = modo !== 'ACTUAL'

  return (
    <div
      className={`${styles.barra} ${compacto ? styles.barraCompacta : ''} ${
        exploracionActiva ? styles.barraActiva : ''
      }`}
      // Cuando el contexto global ya fija el individuo, se explica aquí en vez
      // de añadir una fila permanente a cada tarjeta.
      title={
        hayIndividuoGlobal
          ? `El contexto global ya está limitado al paciente ${individuoGlobal}.`
          : undefined
      }
    >
      <div className={styles.filaPrincipal}>
        {puedeAnalizar && (
          // Segmented control: las opciones se ven sin desplegar nada, y el
          // modo activo se reconoce de un vistazo. role="group" + aria-pressed
          // lo hace navegable y anunciable sin librerías.
          <div className={styles.segmentado} role="group" aria-label="Analizar por">
            {modos.map((m) => (
              <button
                key={m.valor}
                type="button"
                title={m.titulo}
                aria-pressed={modo === m.valor}
                disabled={ocupado}
                className={`${styles.segmento} ${modo === m.valor ? styles.segmentoActivo : ''}`}
                onClick={() => onModo(m.valor)}
              >
                {m.etiqueta}
              </button>
            ))}
          </div>
        )}

        {/* El alcance solo se anuncia cuando hay exploración activa: en modo
            Actual no hay nada local que advertir, y mostrarlo sugería una
            exploración temporal que no existe. */}
        <div className={styles.ladoDerecho}>
          {exploracionActiva && (
            <span className={styles.alcanceLocal}>Exploración local · Solo este indicador</span>
          )}
          {puedeElegirVista && (
            <label className={styles.visualizacion} htmlFor={`${id}-visualizacion`}>
              <span className={styles.visualizacionEtiqueta}>Gráfico</span>
              <select
                id={`${id}-visualizacion`}
                className={`${styles.select} ${styles.selectVisualizacion}`}
                value={visualizacionActual}
                disabled={ocupado}
                onChange={(e) => onVisualizacion(e.target.value)}
              >
                {opcionesVisualizacion.map((o) => (
                  <option key={o.valor} value={o.valor}>
                    {o.etiqueta}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
      </div>

      {/* Campo contextual: solo existe en el modo que lo necesita, así que en
          "Actual" no queda ningún hueco vacío ocupando altura. */}
      {modo === 'CATEGORIA' && soportaCategoria && (
        <div className={styles.contextual}>
          <label className={styles.etiqueta} htmlFor={`${id}-agrupacion`}>
            Agrupar por
          </label>
          <select
            id={`${id}-agrupacion`}
            className={styles.select}
            value={campoAgrupacion}
            disabled={ocupado}
            onChange={(e) => onCampoAgrupacion(e.target.value)}
          >
            <option value="">— seleccionar campo —</option>
            {camposAgrupables.map((c: CampoFiltroCategoria) => (
              <option key={c.codigo} value={c.codigo}>
                {c.etiqueta}
              </option>
            ))}
          </select>
        </div>
      )}

      {modo === 'INDIVIDUO' && campoIndividuo && (
        <div className={styles.contextual}>
          <label className={styles.etiqueta} htmlFor={`${id}-individuo`}>
            {campoIndividuo.etiqueta}
          </label>
          <select
            id={`${id}-individuo`}
            className={styles.select}
            value={valorIndividuo}
            disabled={ocupado}
            onChange={(e) => onValorIndividuo(e.target.value)}
          >
            <option value="">Seleccionar individuo…</option>
            {campoIndividuo.valores.map((v) => (
              <option key={v} value={v}>
                {v}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  )
}
