import { useState } from 'react'
import styles from './CorreccionFilasTrabajo.module.css'

interface EditorCeldaTrabajoProps {
  /** TipoDato del mapeo (TEXTO/ENTERO/DECIMAL/FECHA/BOOLEANO), o null si no se conoce. */
  tipoDato: string | null
  valorInicial: string
  disabled?: boolean
  /** Si la celda tiene un error bloqueante: la ayuda de tipo se muestra más visible. */
  esBloqueante?: boolean
  onGuardar: (valor: string) => void
}

// Ayuda visual del tipo esperado, mostrada bajo el input al editar una celda.
// Si tipoDato es null (columna sin mapeo conocido) no se muestra nada: no hay
// nada fiable que decir sobre el formato esperado.
function ayudaTipoDato(tipoDato: string | null): string | null {
  switch (tipoDato) {
    case 'FECHA':
      return 'Tipo esperado: fecha. Formato recomendado: AAAA-MM-DD. Ejemplo: 2026-07-29.'
    case 'ENTERO':
      return 'Tipo esperado: número entero. Ejemplo: 76.'
    case 'DECIMAL':
      return 'Tipo esperado: número decimal. Ejemplo: 76.5.'
    case 'BOOLEANO':
      return 'Tipo esperado: Sí/No.'
    case 'TEXTO':
      return 'Tipo esperado: texto.'
    default:
      return null
  }
}

function mensajeVacioTipoDato(tipoDato: string | null): string {
  switch (tipoDato) {
    case 'FECHA':
      return 'Introduce una fecha válida o excluye la fila.'
    case 'ENTERO':
      return 'Introduce un número entero válido o excluye la fila.'
    case 'DECIMAL':
      return 'Introduce un número decimal válido o excluye la fila.'
    case 'BOOLEANO':
      return 'Selecciona Sí o No, o excluye la fila.'
    default:
      return 'Introduce un valor o excluye la fila.'
  }
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

export function EditorCeldaTrabajo({
  tipoDato,
  valorInicial,
  disabled,
  esBloqueante,
  onGuardar,
}: EditorCeldaTrabajoProps) {
  const esFecha = tipoDato === 'FECHA'
  const fechaIsoInicial = esFecha ? normalizarFechaIso(valorInicial) : null
  const valorInicialEditor = fechaIsoInicial ?? valorInicial
  const [valor, setValor] = useState(valorInicialEditor)
  const [avisoVacio, setAvisoVacio] = useState(false)

  const sinCambios = valor === valorInicialEditor

  const cambiarValor = (nuevo: string) => {
    setValor(nuevo)
    if (avisoVacio) setAvisoVacio(false)
  }

  // No tiene sentido guardar una celda vacía: el problema que se quiere
  // resolver es justo que falta un valor, así que sin texto no hay nada que
  // enviar al backend. La alternativa real para esa fila es excluirla.
  const manejarGuardar = () => {
    if (valor.trim() === '') {
      setAvisoVacio(true)
      return
    }
    onGuardar(valor)
  }

  const ayuda = ayudaTipoDato(tipoDato)

  return (
    <div className={styles.editorCeldaContenedor}>
      <div className={styles.editorCelda}>
        {tipoDato === 'BOOLEANO' ? (
          <select value={valor} disabled={disabled} onChange={(e) => cambiarValor(e.target.value)}>
            {OPCIONES_BOOLEANO.map((o) => (
              <option key={o.valor} value={o.valor}>
                {o.etiqueta}
              </option>
            ))}
          </select>
        ) : esFecha && fechaIsoInicial !== null ? (
          <input type="date" value={valor} disabled={disabled} onChange={(e) => cambiarValor(e.target.value)} />
        ) : tipoDato === 'ENTERO' ? (
          <input
            type="number"
            step="1"
            value={valor}
            disabled={disabled}
            onChange={(e) => cambiarValor(e.target.value)}
          />
        ) : tipoDato === 'DECIMAL' ? (
          <input
            type="number"
            step="any"
            value={valor}
            disabled={disabled}
            onChange={(e) => cambiarValor(e.target.value)}
          />
        ) : (
          <input type="text" value={valor} disabled={disabled} onChange={(e) => cambiarValor(e.target.value)} />
        )}
        <button
          type="button"
          className="btn btnPrimary"
          disabled={disabled || sinCambios}
          onClick={manejarGuardar}
        >
          Guardar
        </button>
      </div>
      {ayuda && (
        <p className={esBloqueante ? styles.ayudaTipoDatoBloqueante : styles.ayudaTipoDato}>{ayuda}</p>
      )}
      {avisoVacio && <p className={styles.avisoVacio}>{mensajeVacioTipoDato(tipoDato)}</p>}
    </div>
  )
}
