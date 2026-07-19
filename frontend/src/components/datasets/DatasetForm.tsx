import { useState } from 'react'
import { Link } from 'react-router'
import type { DatasetClinicoRequestDto } from '../../api/types'
import { FormField } from '../FormField'
import styles from './DatasetForm.module.css'

export interface DatasetFormValores {
  codigo: string
  nombre: string
  descripcion: string
}

interface DatasetFormProps {
  valorInicial: DatasetFormValores
  onSubmit: (payload: DatasetClinicoRequestDto) => void
  guardando: boolean
  textoBoton: string
  cancelarHref: string
}

export function DatasetForm({ valorInicial, onSubmit, guardando, textoBoton, cancelarHref }: DatasetFormProps) {
  const [codigo, setCodigo] = useState(valorInicial.codigo)
  const [nombre, setNombre] = useState(valorInicial.nombre)
  const [descripcion, setDescripcion] = useState(valorInicial.descripcion)
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
