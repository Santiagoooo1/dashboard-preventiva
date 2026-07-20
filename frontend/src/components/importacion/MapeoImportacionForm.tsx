import { useState } from 'react'
import type {
  CampoClinicoResponseDto,
  MapeoCampoImportacionRequestDto,
  OpcionCatalogoDto,
  TipoDato,
} from '../../api/types'
import { FormField } from '../FormField'
import styles from './Formularios.module.css'

export interface MapeoFormValores {
  nombreColumnaOrigen: string
  campoClinicoId: string
  tipoDato: string
  obligatorio: boolean
  politicaCampoFaltante: string
  valorPorDefecto: string
  orden: string
}

interface MapeoImportacionFormProps {
  valorInicial: MapeoFormValores
  campos: CampoClinicoResponseDto[]
  tiposDato: OpcionCatalogoDto[]
  politicas: OpcionCatalogoDto[]
  columnasSugeridas: string[]
  onSubmit: (payload: MapeoCampoImportacionRequestDto) => void
  onCancelar: () => void
  guardando: boolean
  textoBoton: string
}

export function MapeoImportacionForm({
  valorInicial,
  campos,
  tiposDato,
  politicas,
  columnasSugeridas,
  onSubmit,
  onCancelar,
  guardando,
  textoBoton,
}: MapeoImportacionFormProps) {
  const [nombreColumnaOrigen, setNombreColumnaOrigen] = useState(valorInicial.nombreColumnaOrigen)
  const [campoClinicoId, setCampoClinicoId] = useState(valorInicial.campoClinicoId)
  const [tipoDato, setTipoDato] = useState(valorInicial.tipoDato)
  const [obligatorio, setObligatorio] = useState(valorInicial.obligatorio)
  const [politica, setPolitica] = useState(valorInicial.politicaCampoFaltante)
  const [valorPorDefecto, setValorPorDefecto] = useState(valorInicial.valorPorDefecto)
  const [orden, setOrden] = useState(valorInicial.orden)
  const [errores, setErrores] = useState<Record<string, string>>({})

  // Al elegir campo clínico se propone su tipo de dato, que es el que espera el motor.
  const alCambiarCampo = (id: string) => {
    setCampoClinicoId(id)
    const campo = campos.find((c) => String(c.id) === id)
    if (campo && !tipoDato) {
      setTipoDato(campo.tipoDato)
    }
  }

  const enviar = () => {
    const nuevosErrores: Record<string, string> = {}
    if (!nombreColumnaOrigen.trim()) nuevosErrores.nombreColumnaOrigen = 'El nombre de la columna es obligatorio.'
    if (!campoClinicoId) nuevosErrores.campoClinicoId = 'Selecciona el campo clínico de destino.'
    if (!tipoDato) nuevosErrores.tipoDato = 'Selecciona el tipo de dato.'
    setErrores(nuevosErrores)
    if (Object.keys(nuevosErrores).length > 0) return

    onSubmit({
      nombreColumnaOrigen: nombreColumnaOrigen.trim(),
      campoClinicoId: Number(campoClinicoId),
      tipoDato: tipoDato as TipoDato,
      obligatorio,
      politicaCampoFaltante: politica || null,
      valorPorDefecto: valorPorDefecto.trim() || null,
      orden: orden === '' ? null : Number(orden),
    })
  }

  return (
    <div className={styles.form}>
      <div className={styles.grid}>
        <FormField
          label="Nombre de la columna en el archivo"
          help="Debe coincidir con la cabecera del archivo. Usa “Detectar columnas” para verlas."
          error={errores.nombreColumnaOrigen}
        >
          <input
            list="columnas-sugeridas"
            value={nombreColumnaOrigen}
            onChange={(e) => setNombreColumnaOrigen(e.target.value)}
          />
        </FormField>
        <datalist id="columnas-sugeridas">
          {columnasSugeridas.map((c) => (
            <option key={c} value={c} />
          ))}
        </datalist>
        <FormField
          label="Campo clínico de destino"
          help="Campo del dataset donde se guardará el valor."
          error={errores.campoClinicoId}
        >
          <select value={campoClinicoId} onChange={(e) => alCambiarCampo(e.target.value)}>
            <option value="">— seleccionar campo —</option>
            {campos.map((c) => (
              <option key={c.id} value={c.id}>
                {c.etiqueta} ({c.codigo})
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Tipo de dato" help="Debe coincidir con el tipo del campo destino." error={errores.tipoDato}>
          <select value={tipoDato} onChange={(e) => setTipoDato(e.target.value)}>
            <option value="">— seleccionar tipo —</option>
            {tiposDato.map((t) => (
              <option key={t.codigo} value={t.codigo}>
                {t.nombre}
              </option>
            ))}
          </select>
        </FormField>
        <FormField
          label="Política si falta el valor"
          help="Qué hacer cuando la columna no trae dato en una fila."
        >
          <select value={politica} onChange={(e) => setPolitica(e.target.value)}>
            <option value="">— por defecto —</option>
            {politicas.map((p) => (
              <option key={p.codigo} value={p.codigo}>
                {p.nombre}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Valor por defecto" help="Se usará si la columna viene vacía.">
          <input value={valorPorDefecto} onChange={(e) => setValorPorDefecto(e.target.value)} />
        </FormField>
        <FormField label="Orden" help="Número opcional para ordenar los mapeos.">
          <input type="number" step={1} value={orden} onChange={(e) => setOrden(e.target.value)} />
        </FormField>
      </div>

      <div className={styles.checkboxRow}>
        <input
          id="mapeoObligatorio"
          type="checkbox"
          checked={obligatorio}
          onChange={(e) => setObligatorio(e.target.checked)}
        />
        <label htmlFor="mapeoObligatorio">La columna es obligatoria</label>
      </div>

      <div className={styles.botones}>
        <button type="button" className="btn btnPrimary" disabled={guardando} onClick={enviar}>
          {textoBoton}
        </button>
        <button type="button" className="btn btnSecondary" onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </div>
  )
}
