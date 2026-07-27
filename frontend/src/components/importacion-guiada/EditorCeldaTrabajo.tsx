import { useState } from 'react'
import styles from './CorreccionFilasTrabajo.module.css'

interface EditorCeldaTrabajoProps {
  /** TipoDato del mapeo (TEXTO/ENTERO/DECIMAL/FECHA/BOOLEANO), o null si no se conoce. */
  tipoDato: string | null
  valorInicial: string
  disabled?: boolean
  onGuardar: (valor: string) => void
}

const OPCIONES_BOOLEANO = [
  { valor: '', etiqueta: '—' },
  { valor: 'SI', etiqueta: 'Sí' },
  { valor: 'NO', etiqueta: 'No' },
]

/**
 * Intenta convertir dd/MM/yyyy, dd-MM-yyyy o yyyy-MM-dd a yyyy-MM-dd (el
 * formato que exige <input type="date">). Si no reconoce el formato,
 * devuelve null y el editor cae a un input de texto libre: el backend es
 * quien valida el formato final, no el frontend.
 */
function normalizarFechaIso(valor: string): string | null {
  const texto = valor.trim()
  if (/^\d{4}-\d{2}-\d{2}$/.test(texto)) return texto

  const conBarra = texto.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/)
  if (conBarra) {
    const [, d, m, y] = conBarra
    return `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`
  }

  const conGuion = texto.match(/^(\d{1,2})-(\d{1,2})-(\d{4})$/)
  if (conGuion) {
    const [, d, m, y] = conGuion
    return `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`
  }

  return null
}

export function EditorCeldaTrabajo({ tipoDato, valorInicial, disabled, onGuardar }: EditorCeldaTrabajoProps) {
  const esFecha = tipoDato === 'FECHA'
  const fechaIsoInicial = esFecha ? normalizarFechaIso(valorInicial) : null
  const valorInicialEditor = fechaIsoInicial ?? valorInicial
  const [valor, setValor] = useState(valorInicialEditor)

  const sinCambios = valor === valorInicialEditor

  return (
    <div className={styles.editorCelda}>
      {tipoDato === 'BOOLEANO' ? (
        <select value={valor} disabled={disabled} onChange={(e) => setValor(e.target.value)}>
          {OPCIONES_BOOLEANO.map((o) => (
            <option key={o.valor} value={o.valor}>
              {o.etiqueta}
            </option>
          ))}
        </select>
      ) : esFecha && fechaIsoInicial !== null ? (
        <input type="date" value={valor} disabled={disabled} onChange={(e) => setValor(e.target.value)} />
      ) : tipoDato === 'ENTERO' ? (
        <input type="number" step="1" value={valor} disabled={disabled} onChange={(e) => setValor(e.target.value)} />
      ) : tipoDato === 'DECIMAL' ? (
        <input type="number" step="any" value={valor} disabled={disabled} onChange={(e) => setValor(e.target.value)} />
      ) : (
        <input type="text" value={valor} disabled={disabled} onChange={(e) => setValor(e.target.value)} />
      )}
      <button
        type="button"
        className="btn btnPrimary"
        disabled={disabled || sinCambios}
        onClick={() => onGuardar(valor)}
      >
        Guardar
      </button>
    </div>
  )
}
