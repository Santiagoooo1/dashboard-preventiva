import type { Granularidad, OpcionCatalogoDto } from '../../api/types'
import styles from './DashboardFilters.module.css'

export interface ValoresFiltros {
  fechaDesde: string
  fechaHasta: string
  granularidad: string
  campoFecha: string
}

export const FILTROS_VACIOS: ValoresFiltros = {
  fechaDesde: '',
  fechaHasta: '',
  granularidad: '',
  campoFecha: '',
}

interface DashboardFiltersProps {
  valores: ValoresFiltros
  onChange: (valores: ValoresFiltros) => void
  onAplicar: () => void
  onLimpiar: () => void
  granularidades: OpcionCatalogoDto[]
  camposFechaPermitidos: string[] | null
  errorMetadata: string | null
  cargando: boolean
}

/** Convierte los valores del formulario al body que espera el backend. */
export function aRequest(valores: ValoresFiltros) {
  return {
    fechaDesde: valores.fechaDesde || null,
    fechaHasta: valores.fechaHasta || null,
    granularidad: (valores.granularidad || null) as Granularidad | null,
    campoFecha: valores.campoFecha || null,
  }
}

export function DashboardFilters({
  valores,
  onChange,
  onAplicar,
  onLimpiar,
  granularidades,
  camposFechaPermitidos,
  errorMetadata,
  cargando,
}: DashboardFiltersProps) {
  const set = (cambios: Partial<ValoresFiltros>) => onChange({ ...valores, ...cambios })

  return (
    <div className={styles.wrap}>
      <div className={styles.filtros}>
        <div className={styles.campos}>
          <div className={styles.campo}>
            <label htmlFor="fechaDesde">Desde</label>
            <input
              id="fechaDesde"
              type="date"
              value={valores.fechaDesde}
              onChange={(e) => set({ fechaDesde: e.target.value })}
            />
          </div>
          <div className={styles.campo}>
            <label htmlFor="fechaHasta">Hasta</label>
            <input
              id="fechaHasta"
              type="date"
              value={valores.fechaHasta}
              onChange={(e) => set({ fechaHasta: e.target.value })}
            />
          </div>
          <div className={styles.campo}>
            <label htmlFor="granularidad">Granularidad</label>
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
          {camposFechaPermitidos && camposFechaPermitidos.length > 0 && (
            <div className={styles.campo}>
              <label htmlFor="campoFecha">Campo de fecha</label>
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
        <div className={styles.acciones}>
          <button type="button" className="btn btnSecondary" disabled={cargando} onClick={onLimpiar}>
            Limpiar
          </button>
          <button type="button" className="btn btnPrimary" disabled={cargando} onClick={onAplicar}>
            {cargando ? 'Aplicando…' : 'Aplicar filtros'}
          </button>
        </div>
      </div>
      {errorMetadata && (
        <p className={styles.avisoMetadata}>
          No se han podido cargar los campos de fecha disponibles ({errorMetadata}). El resto de filtros sigue
          funcionando.
        </p>
      )}
    </div>
  )
}
