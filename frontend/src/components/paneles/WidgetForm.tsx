import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import type {
  MetricaClinicaResponseDto,
  PanelMetricaRequestDto,
  TipoVisualizacion,
  TipoVisualizacionCatalogoDto,
} from '../../api/types'
import { FormField } from '../FormField'
import styles from './WidgetForm.module.css'

export interface WidgetFormValores {
  metricaId: string
  tituloPersonalizado: string
  descripcionPersonalizada: string
  tipoVisualizacion: string
  orden: string
  ancho: string
}

interface WidgetFormProps {
  valorInicial: WidgetFormValores
  metricasDisponibles: MetricaClinicaResponseDto[]
  tipoVisualizaciones: TipoVisualizacionCatalogoDto[]
  datasetId: string
  esEdicion: boolean
  onSubmit: (payload: PanelMetricaRequestDto) => void
  onCancelar: () => void
  guardando: boolean
}

/** Nombre en pantalla de cada operación. El enum crudo no le dice nada a nadie. */
const NOMBRE_OPERACION: Record<string, string> = {
  CONTEO: 'Conteo',
  CONTEO_DISTINTO: 'Valores distintos',
  PORCENTAJE: 'Porcentaje',
  COMPLETITUD: 'Completitud',
  PROMEDIO: 'Media',
  MEDIANA: 'Mediana',
  SUMA: 'Suma',
  MINIMO: 'Mínimo',
  MAXIMO: 'Máximo',
  DISTRIBUCION: 'Distribución',
  CATEGORIA_PRINCIPAL: 'Categoría más frecuente',
}

/** Forma del resultado, deducida de la operación. */
function formaResultado(tipoMetrica: string): 'UNICO' | 'REPARTO' {
  return tipoMetrica === 'DISTRIBUCION' ? 'REPARTO' : 'UNICO'
}

/** Sobre qué columna opera la métrica, si opera sobre alguna. */
function campoOrigen(metrica: MetricaClinicaResponseDto): string | null {
  return metrica.configuracion?.campoValor ?? metrica.configuracion?.campoAgrupacion ?? null
}

export function WidgetForm({
  valorInicial,
  metricasDisponibles,
  tipoVisualizaciones,
  datasetId,
  esEdicion,
  onSubmit,
  onCancelar,
  guardando,
}: WidgetFormProps) {
  const [metricaId, setMetricaId] = useState(valorInicial.metricaId)
  const [titulo, setTitulo] = useState(valorInicial.tituloPersonalizado)
  const [descripcion, setDescripcion] = useState(valorInicial.descripcionPersonalizada)
  const [tipoVisualizacion, setTipoVisualizacion] = useState(valorInicial.tipoVisualizacion)
  const [orden, setOrden] = useState(valorInicial.orden)
  const [ancho, setAncho] = useState(valorInicial.ancho)
  const [errores, setErrores] = useState<Record<string, string>>({})

  // --- Búsqueda y filtros del catálogo de métricas ---
  // Un desplegable plano deja de servir en cuanto el dataset pasa de una docena
  // de métricas, y el constructor de esta fase hace fácil llegar a decenas. Se
  // filtra antes de elegir, no revisando la lista entera.
  const [busqueda, setBusqueda] = useState('')
  const [filtroForma, setFiltroForma] = useState('')
  const [filtroCampo, setFiltroCampo] = useState('')

  const camposOrigen = useMemo(() => {
    const codigos = new Set<string>()
    for (const m of metricasDisponibles) {
      const campo = campoOrigen(m)
      if (campo) codigos.add(campo)
    }
    return [...codigos].sort()
  }, [metricasDisponibles])

  const metricasFiltradas = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    return metricasDisponibles.filter((m) => {
      if (texto && !m.nombre.toLowerCase().includes(texto) && !m.codigo.toLowerCase().includes(texto)) {
        return false
      }
      if (filtroForma && formaResultado(m.tipoMetrica) !== filtroForma) return false
      if (filtroCampo && campoOrigen(m) !== filtroCampo) return false
      return true
    })
  }, [metricasDisponibles, busqueda, filtroForma, filtroCampo])

  const enviar = () => {
    const nuevosErrores: Record<string, string> = {}
    if (!metricaId) nuevosErrores.metricaId = 'Selecciona una métrica.'
    if (!tipoVisualizacion) nuevosErrores.tipoVisualizacion = 'Selecciona el tipo de visualización.'
    setErrores(nuevosErrores)
    if (Object.keys(nuevosErrores).length > 0) return

    // En edición se reenvían SIEMPRE los seis campos: el backend resetea
    // orden a 0 y ancho a 3 si el PUT no los incluye.
    onSubmit({
      metricaId: Number(metricaId),
      tituloPersonalizado: titulo.trim() || null,
      descripcionPersonalizada: descripcion.trim() || null,
      tipoVisualizacion: tipoVisualizacion as TipoVisualizacion,
      orden: orden === '' ? null : Number(orden),
      ancho: ancho === '' ? null : Number(ancho),
    })
  }

  return (
    <div className={styles.form}>
      {!esEdicion && (
        <div className={styles.buscador}>
          <FormField label="Buscar métrica">
            <input
              type="search"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Por nombre o código…"
            />
          </FormField>
          <FormField label="Tipo de resultado">
            <select value={filtroForma} onChange={(e) => setFiltroForma(e.target.value)}>
              <option value="">Todos</option>
              <option value="UNICO">Valor único</option>
              <option value="REPARTO">Reparto por categorías</option>
            </select>
          </FormField>
          <FormField label="Campo de origen">
            <select value={filtroCampo} onChange={(e) => setFiltroCampo(e.target.value)}>
              <option value="">Todos</option>
              {camposOrigen.map((codigo) => (
                <option key={codigo} value={codigo}>
                  {codigo}
                </option>
              ))}
            </select>
          </FormField>
        </div>
      )}

      <div className={styles.grid}>
        <FormField
          label="Métrica"
          help={
            esEdicion
              ? 'La métrica de un widget no se cambia: quita el widget y añade otro.'
              : `Mostrando ${metricasFiltradas.length} de ${metricasDisponibles.length} métricas del dataset.`
          }
          error={errores.metricaId}
        >
          <select value={metricaId} disabled={esEdicion} onChange={(e) => setMetricaId(e.target.value)}>
            <option value="">— seleccionar métrica —</option>
            {metricasFiltradas.map((m) => {
              const campo = campoOrigen(m)
              return (
                <option key={m.id} value={m.id}>
                  {m.nombre} — {NOMBRE_OPERACION[m.tipoMetrica] ?? m.tipoMetrica}
                  {campo ? ` · ${campo}` : ''}
                  {formaResultado(m.tipoMetrica) === 'REPARTO' ? ' · reparto' : ''}
                </option>
              )
            })}
          </select>
        </FormField>
        <FormField
          label="Tipo de visualización"
          help="Cómo se muestra esta métrica en el dashboard: como número (indicador), gráfico o tabla. Puedes cambiarlo también directamente desde el propio dashboard, en cada widget."
          error={errores.tipoVisualizacion}
        >
          <select value={tipoVisualizacion} onChange={(e) => setTipoVisualizacion(e.target.value)}>
            <option value="">— seleccionar visualización —</option>
            {tipoVisualizaciones.map((v) => (
              <option key={v.codigo} value={v.codigo}>
                {v.nombre}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Título personalizado">
          <input value={titulo} onChange={(e) => setTitulo(e.target.value)} />
        </FormField>
        <FormField label="Descripción personalizada">
          <input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
        </FormField>
        <FormField label="Orden" help="Posición del widget dentro del panel: los números más bajos van primero.">
          <input type="number" step={1} value={orden} onChange={(e) => setOrden(e.target.value)} />
        </FormField>
        <FormField label="Tamaño" help="Cuánto sitio ocupa el widget en el dashboard." error={errores.ancho}>
          <select value={ancho} onChange={(e) => setAncho(e.target.value)}>
            <option value="3">Pequeño</option>
            <option value="6">Medio</option>
            <option value="12">Ancho completo</option>
          </select>
        </FormField>
      </div>

      {!esEdicion && (
        <p className={styles.ayudaCrear}>
          ¿No encuentras la métrica?{' '}
          <Link to={`/datasets/${datasetId}/metricas/nueva/desde-columna`}>Crear una nueva desde una columna.</Link>
        </p>
      )}

      <div className={styles.botones}>
        <button type="button" className="btn btnPrimary" disabled={guardando} onClick={enviar}>
          {esEdicion ? 'Guardar cambios' : 'Añadir widget'}
        </button>
        <button type="button" className="btn btnSecondary" onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </div>
  )
}
