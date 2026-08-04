import type { DashboardPanelRequestDto, FiltroMetricaDto, Granularidad, OpcionCatalogoDto } from '../../api/types'
import { etiquetaValor } from './camposFiltroDashboard'
import type { CampoFiltroCategoria } from './camposFiltroDashboard'
import styles from './DashboardFilters.module.css'

export interface ValoresFiltros {
  fechaDesde: string
  fechaHasta: string
  granularidad: string
  campoFecha: string
  paciente: string
  camposCategoria: Record<string, string>
}

export const FILTROS_VACIOS: ValoresFiltros = {
  fechaDesde: '',
  fechaHasta: '',
  granularidad: '',
  campoFecha: '',
  paciente: '',
  camposCategoria: {},
}

interface DashboardFiltersProps {
  valores: ValoresFiltros
  /** Filtros realmente aplicados al dashboard: de aquí salen los chips, no del formulario. */
  aplicados: ValoresFiltros
  onChange: (valores: ValoresFiltros) => void
  onAplicar: () => void
  onLimpiar: () => void
  onAplicarValores: (valores: ValoresFiltros) => void
  granularidades: OpcionCatalogoDto[]
  camposFechaPermitidos: string[] | null
  errorMetadata: string | null
  cargando: boolean
  tienePaciente: boolean
  valoresPaciente: string[]
  camposPrincipales: CampoFiltroCategoria[]
  camposAvanzados: CampoFiltroCategoria[]
  errorCamposCategoria: string | null
}

/** Convierte los valores del formulario al body que espera el backend. */
export function aRequest(valores: ValoresFiltros): DashboardPanelRequestDto {
  const filtros: FiltroMetricaDto[] = []

  if (valores.paciente.trim()) {
    filtros.push({ campo: 'pacienteCodigo', operador: 'CONTAINS', valor: valores.paciente.trim() })
  }

  for (const [campo, valor] of Object.entries(valores.camposCategoria)) {
    if (valor) {
      filtros.push({ campo, operador: 'EQ', valor })
    }
  }

  return {
    fechaDesde: valores.fechaDesde || null,
    fechaHasta: valores.fechaHasta || null,
    granularidad: (valores.granularidad || null) as Granularidad | null,
    campoFecha: valores.campoFecha || null,
    filtros: filtros.length > 0 ? filtros : null,
  }
}

/** Compara dos conjuntos de filtros ignorando claves vacías, para detectar cambios sin aplicar. */
function mismosFiltros(a: ValoresFiltros, b: ValoresFiltros): boolean {
  const clave = (v: ValoresFiltros) =>
    JSON.stringify([
      v.fechaDesde,
      v.fechaHasta,
      v.granularidad,
      v.campoFecha,
      v.paciente.trim(),
      Object.entries(v.camposCategoria)
        .filter(([, valor]) => Boolean(valor))
        .sort(([x], [y]) => x.localeCompare(y)),
    ])

  return clave(a) === clave(b)
}

function contarAvanzadosActivos(
  valores: ValoresFiltros,
  camposAvanzados: CampoFiltroCategoria[],
): number {
  return camposAvanzados.filter((c) => Boolean(valores.camposCategoria[c.codigo])).length
}

interface Chip {
  clave: string
  etiqueta: string
  sinEsteFiltro: ValoresFiltros
}

function construirChips(valores: ValoresFiltros, campos: CampoFiltroCategoria[]): Chip[] {
  const chips: Chip[] = []

  if (valores.fechaDesde) {
    chips.push({
      clave: 'fechaDesde',
      etiqueta: `Desde: ${valores.fechaDesde}`,
      sinEsteFiltro: { ...valores, fechaDesde: '' },
    })
  }

  if (valores.fechaHasta) {
    chips.push({
      clave: 'fechaHasta',
      etiqueta: `Hasta: ${valores.fechaHasta}`,
      sinEsteFiltro: { ...valores, fechaHasta: '' },
    })
  }

  if (valores.paciente.trim()) {
    chips.push({
      clave: 'paciente',
      etiqueta: `Paciente: ${valores.paciente.trim()}`,
      sinEsteFiltro: { ...valores, paciente: '' },
    })
  }

  for (const [codigo, valor] of Object.entries(valores.camposCategoria)) {
    if (!valor) continue
    const campo = campos.find((c) => c.codigo === codigo)
    chips.push({
      clave: `campo:${codigo}`,
      etiqueta: `${campo?.etiqueta ?? codigo}: ${campo ? etiquetaValor(campo, valor) : valor}`,
      sinEsteFiltro: {
        ...valores,
        camposCategoria: { ...valores.camposCategoria, [codigo]: '' },
      },
    })
  }

  return chips
}

export function DashboardFilters({
  valores,
  aplicados,
  onChange,
  onAplicar,
  onLimpiar,
  onAplicarValores,
  granularidades,
  camposFechaPermitidos,
  errorMetadata,
  cargando,
  tienePaciente,
  valoresPaciente,
  camposPrincipales,
  camposAvanzados,
  errorCamposCategoria,
}: DashboardFiltersProps) {
  const set = (cambios: Partial<ValoresFiltros>) => onChange({ ...valores, ...cambios })
  const setCampo = (codigo: string, valor: string) =>
    onChange({ ...valores, camposCategoria: { ...valores.camposCategoria, [codigo]: valor } })

  const todosLosCampos = [...camposPrincipales, ...camposAvanzados]
  const chips = construirChips(aplicados, todosLosCampos)
  const avanzadosActivos = contarAvanzadosActivos(valores, camposAvanzados)
  const hayCambiosSinAplicar = !mismosFiltros(valores, aplicados)
  const pacienteAplicado = aplicados.paciente.trim()

  const selectorCampo = (campo: CampoFiltroCategoria) => (
    <div className={styles.campo} key={campo.codigo}>
      <label htmlFor={`filtro-${campo.codigo}`}>{campo.etiqueta}</label>
      <select
        id={`filtro-${campo.codigo}`}
        value={valores.camposCategoria[campo.codigo] ?? ''}
        onChange={(e) => setCampo(campo.codigo, e.target.value)}
      >
        <option value="">— todos —</option>
        {campo.valores.map((v) => (
          <option key={v} value={v}>
            {etiquetaValor(campo, v)}
          </option>
        ))}
      </select>
    </div>
  )

  return (
    <div className={styles.wrap}>
      {/* Nivel 1 del producto: CONTEXTO. Define la población base sobre la que
          se calcula todo. El badge en azul cobalto fija el ámbito, frente al
          azul cielo "Solo este indicador" de la exploración local. */}
      <div className={styles.cabecera}>
        <div className={styles.cabeceraTexto}>
          <h2 className={styles.cabeceraTitulo}>Contexto del análisis</h2>
          <p className={styles.cabeceraAyuda}>
            Define la población y el periodo que afectan a todo el dashboard.
          </p>
        </div>
        <span className={styles.badgeAlcance}>Afecta a todo el dashboard</span>
      </div>

      {/* Nivel 1 — exploración rápida: paciente + rango de fechas. */}
      <div className={styles.filaPrincipal}>
        {tienePaciente && (
          <div className={`${styles.campo} ${styles.campoPaciente}`}>
            <label htmlFor="filtroPaciente">Paciente / HC</label>
            <input
              id="filtroPaciente"
              type="text"
              list="dashboard-filtro-pacientes"
              placeholder="Buscar por código de paciente…"
              value={valores.paciente}
              onChange={(e) => set({ paciente: e.target.value })}
            />
            <datalist id="dashboard-filtro-pacientes">
              {valoresPaciente.map((p) => (
                <option key={p} value={p} />
              ))}
            </datalist>
            <p className={styles.ayudaCampo}>
              Selecciona un paciente para limitar todo el dashboard a ese caso.
            </p>
          </div>
        )}
        <div className={styles.campo}>
          <label htmlFor="fechaDesde">Fecha desde</label>
          <input
            id="fechaDesde"
            type="date"
            value={valores.fechaDesde}
            onChange={(e) => set({ fechaDesde: e.target.value })}
          />
        </div>
        <div className={styles.campo}>
          <label htmlFor="fechaHasta">Fecha hasta</label>
          <input
            id="fechaHasta"
            type="date"
            value={valores.fechaHasta}
            onChange={(e) => set({ fechaHasta: e.target.value })}
          />
        </div>
      </div>

      {camposPrincipales.length > 0 && (
        <div className={styles.filaSecundaria}>{camposPrincipales.map(selectorCampo)}</div>
      )}

      {/* Nivel 2 — exploración avanzada: cerrada por defecto. <details> mantiene
          los hijos montados al plegarse, así que los valores no se pierden. */}
      {(camposAvanzados.length > 0 || (camposFechaPermitidos?.length ?? 0) > 0 || granularidades.length > 0) && (
        <details className={styles.avanzados}>
          <summary className={styles.avanzadosResumen}>
            <span className={styles.avanzadosFlecha} aria-hidden="true" />
            <span className={styles.avanzadosTexto}>
              <span className={styles.avanzadosTitulo}>Más filtros clínicos</span>
              <span className={styles.avanzadosSubtitulo}>
                Acota el análisis usando variables clínicas específicas.
              </span>
            </span>
            {avanzadosActivos > 0 && (
              <span className={styles.avanzadosContador}>
                {avanzadosActivos} filtro{avanzadosActivos === 1 ? '' : 's'} avanzado
                {avanzadosActivos === 1 ? '' : 's'} activo{avanzadosActivos === 1 ? '' : 's'}
              </span>
            )}
          </summary>

          <div className={styles.avanzadosCuerpo}>
            {camposAvanzados.length > 0 && <div className={styles.campos}>{camposAvanzados.map(selectorCampo)}</div>}

            <div className={styles.campos}>
              {granularidades.length > 0 && (
                <div className={styles.campo}>
                  <label htmlFor="granularidad">Agrupación temporal</label>
                  <select
                    id="granularidad"
                    value={valores.granularidad}
                    onChange={(e) => set({ granularidad: e.target.value })}
                  >
                    <option value="">— sin especificar —</option>
                    {granularidades.map((g) => (
                      <option key={g.codigo} value={g.codigo}>
                        {g.nombre}
                      </option>
                    ))}
                  </select>
                </div>
              )}
              {camposFechaPermitidos && camposFechaPermitidos.length > 0 && (
                <div className={styles.campo}>
                  <label htmlFor="campoFecha">Fecha de referencia</label>
                  <select
                    id="campoFecha"
                    value={valores.campoFecha}
                    onChange={(e) => set({ campoFecha: e.target.value })}
                  >
                    <option value="">— por defecto —</option>
                    {camposFechaPermitidos.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>
          </div>
        </details>
      )}

      <div className={styles.acciones}>
        <button
          type="button"
          className={`btn btnPrimary ${styles.botonAplicar}`}
          disabled={cargando}
          onClick={onAplicar}
        >
          {cargando ? 'Aplicando…' : 'Aplicar filtros'}
        </button>
        <button type="button" className="btn btnSecondary" disabled={cargando} onClick={onLimpiar}>
          Limpiar filtros
        </button>
        {hayCambiosSinAplicar && !cargando && (
          <span className={styles.avisoSinAplicar}>Cambios sin aplicar</span>
        )}
      </div>

      {chips.length > 0 && (
        <div className={styles.aplicadosBloque}>
          <span className={styles.aplicadosTitulo}>Filtros aplicados</span>
          <div className={styles.chips}>
            {chips.map((chip) => (
              <button
                key={chip.clave}
                type="button"
                className={styles.chip}
                disabled={cargando}
                onClick={() => onAplicarValores(chip.sinEsteFiltro)}
                aria-label={`Quitar filtro ${chip.etiqueta}`}
              >
                {chip.etiqueta} <span aria-hidden="true">×</span>
              </button>
            ))}
            <button type="button" className={styles.limpiarTodos} disabled={cargando} onClick={onLimpiar}>
              Limpiar todos
            </button>
          </div>
        </div>
      )}

      {pacienteAplicado && (
        <p className={styles.avisoPaciente}>
          <strong>Paciente {pacienteAplicado} seleccionado.</strong> El dashboard muestra únicamente sus
          registros.{' '}
          <button
            type="button"
            className={styles.avisoPacienteQuitar}
            disabled={cargando}
            onClick={() => onAplicarValores({ ...aplicados, paciente: '' })}
          >
            Quitar filtro de paciente
          </button>
        </p>
      )}

      {errorMetadata && (
        <p className={styles.avisoMetadata}>
          No se han podido cargar los campos de fecha disponibles ({errorMetadata}). El resto de filtros sigue
          funcionando.
        </p>
      )}
      {errorCamposCategoria && (
        <p className={styles.avisoMetadata}>
          No se han podido cargar los filtros clínicos adicionales ({errorCamposCategoria}). Los filtros de fecha
          siguen funcionando.
        </p>
      )}
    </div>
  )
}
