import { useState } from 'react'
import { Link } from 'react-router'
import type { CampoClinicoRequestDto, OpcionCatalogoDto, TipoDato } from '../../api/types'
import { FormField } from '../FormField'
import styles from './CampoForm.module.css'

const CAMPOS_COMUNES_CONOCIDOS = [
  'pacienteCodigo',
  'fechaEvento',
  'servicio',
  'tipoEvento',
  'procedimiento',
  'diagnostico',
  'edad',
  'sexo',
]

export interface CampoFormValores {
  codigo: string
  etiqueta: string
  tipoDato: string
  esComun: boolean
  obligatorio: boolean
  orden: string
}

interface CampoFormProps {
  valorInicial: CampoFormValores
  tiposDato: OpcionCatalogoDto[]
  onSubmit: (payload: CampoClinicoRequestDto) => void
  guardando: boolean
  textoBoton: string
  cancelarHref: string
}

export function CampoForm({
  valorInicial,
  tiposDato,
  onSubmit,
  guardando,
  textoBoton,
  cancelarHref,
}: CampoFormProps) {
  const [codigo, setCodigo] = useState(valorInicial.codigo)
  const [etiqueta, setEtiqueta] = useState(valorInicial.etiqueta)
  const [tipoDato, setTipoDato] = useState(valorInicial.tipoDato)
  const [esComun, setEsComun] = useState(valorInicial.esComun)
  const [obligatorio, setObligatorio] = useState(valorInicial.obligatorio)
  const [orden, setOrden] = useState(valorInicial.orden)
  const [errores, setErrores] = useState<Record<string, string>>({})

  const mostrarAvisoComun = esComun && codigo.trim() !== '' && !CAMPOS_COMUNES_CONOCIDOS.includes(codigo.trim())

  const enviar = () => {
    const nuevosErrores: Record<string, string> = {}
    if (!codigo.trim()) nuevosErrores.codigo = 'El código es obligatorio.'
    if (!etiqueta.trim()) nuevosErrores.etiqueta = 'La etiqueta es obligatoria.'
    if (!tipoDato) nuevosErrores.tipoDato = 'El tipo de dato es obligatorio.'
    setErrores(nuevosErrores)
    if (Object.keys(nuevosErrores).length > 0) return

    onSubmit({
      codigo: codigo.trim(),
      etiqueta: etiqueta.trim(),
      tipoDato: tipoDato as TipoDato,
      esComun,
      obligatorio,
      orden: orden === '' ? null : Number(orden),
    })
  }

  return (
    <div className={styles.form}>
      <FormField label="Código" error={errores.codigo}>
        <input value={codigo} onChange={(e) => setCodigo(e.target.value)} />
      </FormField>
      <FormField label="Etiqueta" error={errores.etiqueta}>
        <input value={etiqueta} onChange={(e) => setEtiqueta(e.target.value)} />
      </FormField>
      <FormField label="Tipo de dato" error={errores.tipoDato}>
        <select value={tipoDato} onChange={(e) => setTipoDato(e.target.value)}>
          <option value="">— seleccionar tipo —</option>
          {tiposDato.map((t) => (
            <option key={t.codigo} value={t.codigo}>
              {t.nombre}
            </option>
          ))}
        </select>
      </FormField>

      <div className={styles.checkboxRow}>
        <input
          id="esComun"
          type="checkbox"
          checked={esComun}
          onChange={(e) => setEsComun(e.target.checked)}
        />
        <label htmlFor="esComun">Es campo común</label>
      </div>
      <p className={styles.ayuda}>
        Marca esComun solo si el código corresponde a un campo común del modelo, por ejemplo pacienteCodigo,
        fechaEvento, servicio, tipoEvento, procedimiento, diagnostico, edad o sexo. Para campos personalizados,
        déjalo desmarcado.
      </p>
      {mostrarAvisoComun && (
        <p className={styles.aviso} role="alert">
          Este código no coincide con ningún campo común conocido; esComun no tendrá efecto de mapeo. Para campos
          personalizados, deja esComun desmarcado.
        </p>
      )}

      <div className={styles.checkboxRow}>
        <input
          id="obligatorio"
          type="checkbox"
          checked={obligatorio}
          onChange={(e) => setObligatorio(e.target.checked)}
        />
        <label htmlFor="obligatorio">Obligatorio</label>
      </div>

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
