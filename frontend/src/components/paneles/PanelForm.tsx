import { useState } from 'react'
import { Link } from 'react-router'
import type { PanelClinicoRequestDto } from '../../api/types'
import { FormField } from '../FormField'
import { avisoCodigo } from '../../utils/validacion'
import styles from './PanelForm.module.css'

export interface PanelFormValores {
  codigo: string
  nombre: string
  descripcion: string
  orden: string
}

interface PanelFormProps {
  valorInicial: PanelFormValores
  onSubmit: (payload: PanelClinicoRequestDto) => void
  guardando: boolean
  textoBoton: string
  cancelarHref: string
}

export function PanelForm({ valorInicial, onSubmit, guardando, textoBoton, cancelarHref }: PanelFormProps) {
  const [codigo, setCodigo] = useState(valorInicial.codigo)
  const [nombre, setNombre] = useState(valorInicial.nombre)
  const [descripcion, setDescripcion] = useState(valorInicial.descripcion)
  const [orden, setOrden] = useState(valorInicial.orden)
  const [errores, setErrores] = useState<Record<string, string>>({})

  const enviar = () => {
    const nuevosErrores: Record<string, string> = {}
    if (!codigo.trim()) nuevosErrores.codigo = 'El código es obligatorio.'
    if (!nombre.trim()) nuevosErrores.nombre = 'El nombre es obligatorio.'
    setErrores(nuevosErrores)
    if (Object.keys(nuevosErrores).length > 0) return

    onSubmit({
      codigo: codigo.trim(),
      nombre: nombre.trim(),
      descripcion: descripcion.trim() || null,
      orden: orden === '' ? null : Number(orden),
    })
  }

  return (
    <div className={styles.form}>
      <FormField
        label="Código interno del panel"
        help="Identificador técnico único dentro del dataset. Ejemplo: panel_ilq_general."
        aviso={avisoCodigo(codigo)}
        error={errores.codigo}
      >
        <input value={codigo} onChange={(e) => setCodigo(e.target.value)} />
      </FormField>
      <FormField label="Nombre" help="Nombre visible del panel." error={errores.nombre}>
        <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </FormField>
      <FormField label="Descripción">
        <textarea rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </FormField>
      <FormField label="Orden" help="Número opcional para ordenar paneles.">
        <input type="number" step={1} value={orden} onChange={(e) => setOrden(e.target.value)} />
      </FormField>
      <div className={styles.botones}>
        <button type="button" className="btn btnPrimary" disabled={guardando} onClick={enviar}>
          {textoBoton}
        </button>
        <Link className="btn btnSecondary" to={cancelarHref}>
          Cancelar
        </Link>
      </div>
    </div>
  )
}
