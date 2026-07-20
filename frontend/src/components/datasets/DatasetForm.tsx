import { useState } from 'react'
import { Link } from 'react-router'
import type { DatasetClinicoRequestDto } from '../../api/types'
import { FormField } from '../FormField'
import { avisoCodigo } from '../../utils/validacion'
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
      <FormField
        label="Código interno"
        help="Identificador técnico único. Usa letras, números y guiones bajos. Ejemplos: ILQ_2026, CAIDAS_2026, CIRUGIAS_ILQ. No uses espacios ni acentos."
        aviso={avisoCodigo(codigo)}
        error={errores.codigo}
      >
        <input value={codigo} onChange={(e) => setCodigo(e.target.value)} />
      </FormField>
      <FormField
        label="Nombre"
        help="Nombre visible para los usuarios. Ejemplo: Infección quirúrgica 2026."
        error={errores.nombre}
      >
        <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </FormField>
      <FormField label="Descripción" help="Explica qué datos contiene este dataset.">
        <textarea rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
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
