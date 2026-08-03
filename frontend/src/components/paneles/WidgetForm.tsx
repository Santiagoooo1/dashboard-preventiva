import { useState } from 'react'
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
  esEdicion: boolean
  onSubmit: (payload: PanelMetricaRequestDto) => void
  onCancelar: () => void
  guardando: boolean
}

export function WidgetForm({
  valorInicial,
  metricasDisponibles,
  tipoVisualizaciones,
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
      <div className={styles.grid}>
        <FormField
          label="Métrica"
          help="Selecciona la métrica que alimentará este widget."
          error={errores.metricaId}
        >
          <select value={metricaId} disabled={esEdicion} onChange={(e) => setMetricaId(e.target.value)}>
            <option value="">— seleccionar métrica —</option>
            {metricasDisponibles.map((m) => (
              <option key={m.id} value={m.id}>
                {m.nombre} ({m.tipoMetrica})
              </option>
            ))}
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
