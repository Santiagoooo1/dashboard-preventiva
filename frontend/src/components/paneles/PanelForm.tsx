import { useState } from 'react'
import { Link } from 'react-router'
import type { PanelClinicoRequestDto } from '../../api/types'
import { FormField } from '../FormField'
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
      <FormField label="Código" error={errores.codigo}>
        <input value={codigo} onChange={(e) => setCodigo(e.target.value)} />
      </FormField>
      <FormField label="Nombre" error={errores.nombre}>
        <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </FormField>
      <FormField label="Descripción">
        <textarea rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </FormField>
      <FormField label="Orden">
        <input type="number" step={1} value={orden} onChange={(e) => setOrden(e.target.value)} />
      </FormField>
      <div className={styles.botones}>
        <button type="button" className={styles.primario} disabled={guardando} onClick={enviar}>
          {textoBoton}
        </button>
        <Link className={styles.secundario} to={cancelarHref}>
          Cancelar
        </Link>
      </div>
    </div>
  )
}
