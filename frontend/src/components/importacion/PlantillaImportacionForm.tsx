import { useState } from 'react'
import { Link } from 'react-router'
import type { OrigenImportacion, PlantillaImportacionRequestDto } from '../../api/types'
import { FormField } from '../FormField'
import styles from './Formularios.module.css'

export interface PlantillaFormValores {
  nombre: string
  descripcion: string
  origen: string
  filaCabecera: string
}

interface PlantillaImportacionFormProps {
  valorInicial: PlantillaFormValores
  onSubmit: (payload: PlantillaImportacionRequestDto) => void
  guardando: boolean
  textoBoton: string
  cancelarHref: string
}

export function PlantillaImportacionForm({
  valorInicial,
  onSubmit,
  guardando,
  textoBoton,
  cancelarHref,
}: PlantillaImportacionFormProps) {
  const [nombre, setNombre] = useState(valorInicial.nombre)
  const [descripcion, setDescripcion] = useState(valorInicial.descripcion)
  const [origen, setOrigen] = useState(valorInicial.origen)
  const [filaCabecera, setFilaCabecera] = useState(valorInicial.filaCabecera)
  const [errores, setErrores] = useState<Record<string, string>>({})

  const enviar = () => {
    const nuevosErrores: Record<string, string> = {}
    if (!nombre.trim()) nuevosErrores.nombre = 'El nombre es obligatorio.'
    if (!origen) nuevosErrores.origen = 'El origen es obligatorio.'
    setErrores(nuevosErrores)
    if (Object.keys(nuevosErrores).length > 0) return

    onSubmit({
      nombre: nombre.trim(),
      descripcion: descripcion.trim() || null,
      origen: origen as OrigenImportacion,
      filaCabecera: filaCabecera === '' ? null : Number(filaCabecera),
    })
  }

  return (
    <div className={styles.form}>
      <FormField label="Nombre" help="Nombre visible de la plantilla." error={errores.nombre}>
        <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </FormField>
      <FormField label="Descripción" help="Explica para qué archivos sirve esta plantilla.">
        <textarea rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </FormField>
      <FormField
        label="Origen"
        help="Indica si la plantilla está pensada para archivos Excel o CSV."
        error={errores.origen}
      >
        <select value={origen} onChange={(e) => setOrigen(e.target.value)}>
          <option value="">— seleccionar origen —</option>
          <option value="EXCEL">Excel</option>
          <option value="CSV">CSV</option>
        </select>
      </FormField>
      <FormField
        label="Fila de cabecera"
        help="Desde qué fila se leen los nombres de las columnas. La primera fila del archivo es la 0."
      >
        <input
          type="number"
          step={1}
          min={0}
          value={filaCabecera}
          onChange={(e) => setFilaCabecera(e.target.value)}
        />
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
