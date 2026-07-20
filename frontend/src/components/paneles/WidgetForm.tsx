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
    if (ancho !== '') {
      const anchoNum = Number(ancho)
      if (!Number.isInteger(anchoNum) || anchoNum < 1 || anchoNum > 12) {
        nuevosErrores.ancho = 'El ancho debe ser un entero entre 1 y 12.'
      }
    }
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
          help="Define cómo se presentará la métrica. En esta fase se renderiza en tarjetas/tablas; los gráficos llegarán más adelante."
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
        <FormField label="Orden">
          <input type="number" step={1} value={orden} onChange={(e) => setOrden(e.target.value)} />
        </FormField>
        <FormField
          label="Ancho"
          help="Ancho del widget en una cuadrícula de 12 columnas. Recomendados: 3, 4, 6 o 12."
          error={errores.ancho}
        >
          <input type="number" step={1} min={1} max={12} value={ancho} onChange={(e) => setAncho(e.target.value)} />
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
