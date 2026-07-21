import { useState } from 'react'
import {
  CAMPOS_CLAVE,
  establecerFechaPrincipal,
  establecerRolClave,
  quitarRolClave,
  sugerirFechaPrincipal,
} from '../../utils/importacionGuiada/camposClave'
import type { RolClaveAsignable } from '../../utils/importacionGuiada/camposClave'
import type { ColumnaConfigurada } from '../../utils/importacionGuiada/sugerenciasColumnas'
import type { ProblemaColumna } from '../../utils/importacionGuiada/sugerenciasErrores'
import { Card } from '../Card'
import styles from './ImportacionGuiada.module.css'

interface CamposClavePanelProps {
  columnas: ColumnaConfigurada[]
  onChange: (columnas: ColumnaConfigurada[]) => void
  problemasPorColumna?: Map<number, ProblemaColumna>
}

function columnaDelRol(columnas: ColumnaConfigurada[], rol: string): ColumnaConfigurada | undefined {
  if (rol === 'fecha') return columnas.find((c) => c.usar && c.codigoInterno === 'fechaEvento')
  return columnas.find((c) => c.usar && c.rol === rol)
}

export function CamposClavePanel({ columnas, onChange, problemasPorColumna }: CamposClavePanelProps) {
  const [verVacios, setVerVacios] = useState(false)

  const columnaPaciente = columnaDelRol(columnas, 'paciente')
  const columnaFechaPrincipal = columnaDelRol(columnas, 'fecha')
  const indiceRecomendado = sugerirFechaPrincipal(columnas)
  const columnaRecomendada = columnas.find((c) => c.indiceColumna === indiceRecomendado)
  const mostrarRecomendacionFecha =
    columnaRecomendada !== undefined && columnaRecomendada.indiceColumna !== columnaFechaPrincipal?.indiceColumna

  const nombresColumnasVacias = problemasPorColumna
    ? [...problemasPorColumna.entries()]
        .filter(([, p]) => p.esValorVacio)
        .map(([indice]) => columnas.find((c) => c.indiceColumna === indice)?.nombreOriginal)
        .filter((nombre): nombre is string => Boolean(nombre))
    : []

  const cambiarCampo = (campo: (typeof CAMPOS_CLAVE)[number], valor: string) => {
    if (campo.rol === 'fecha') {
      if (valor === '') return
      onChange(establecerFechaPrincipal(columnas, Number(valor)))
      return
    }
    const rol = campo.rol as RolClaveAsignable
    if (valor === '') {
      onChange(quitarRolClave(columnas, rol))
      return
    }
    onChange(establecerRolClave(columnas, rol, Number(valor)))
  }

  const aplicarRecomendacionFecha = () => {
    if (indiceRecomendado === null) return
    onChange(establecerFechaPrincipal(columnas, indiceRecomendado))
  }

  const marcarIdentificadorNoObligatorio = () => {
    if (!columnaPaciente) return
    onChange(
      columnas.map((c) => (c.indiceColumna === columnaPaciente.indiceColumna ? { ...c, obligatorio: false } : c)),
    )
  }

  return (
    <Card title="Campos clave del dashboard">
      <div className={styles.camposClaveGrid}>
        {CAMPOS_CLAVE.map((campo) => {
          const opciones = campo.soloFechas
            ? columnas.filter((c) => c.usar && c.rol === 'fecha')
            : columnas.filter((c) => c.usar)
          const seleccionActual = columnaDelRol(columnas, campo.rol)

          return (
            <label key={campo.rol} className={styles.campoClaveItem}>
              <span>{campo.etiqueta}</span>
              <select value={seleccionActual?.indiceColumna ?? ''} onChange={(e) => cambiarCampo(campo, e.target.value)}>
                {!campo.requerido && <option value="">(ninguna)</option>}
                {campo.requerido && !seleccionActual && opciones.length > 0 && (
                  <option value="" disabled>
                    (sin seleccionar)
                  </option>
                )}
                {opciones.length === 0 && <option value="">No hay columnas disponibles</option>}
                {opciones.map((c) => (
                  <option key={c.indiceColumna} value={c.indiceColumna}>
                    {c.nombreOriginal}
                  </option>
                ))}
              </select>
              {campo.ayuda && <span className={styles.campoClaveAyuda}>{campo.ayuda}</span>}
            </label>
          )
        })}
      </div>

      <div className={styles.acciones}>
        {mostrarRecomendacionFecha && (
          <button type="button" className="btn btnSecondary" onClick={aplicarRecomendacionFecha}>
            Usar «{columnaRecomendada.nombreOriginal}» como fecha principal
          </button>
        )}
        {columnaPaciente?.obligatorio && (
          <button type="button" className="btn btnSecondary" onClick={marcarIdentificadorNoObligatorio}>
            Marcar identificador como no obligatorio para esta prueba
          </button>
        )}
        {nombresColumnasVacias.length > 0 && (
          <button type="button" className="btn btnSecondary" onClick={() => setVerVacios((v) => !v)}>
            Ver columnas con valores vacíos
          </button>
        )}
      </div>

      {verVacios && nombresColumnasVacias.length > 0 && (
        <p className={styles.campoClaveAyuda}>Columnas con valores vacíos: {nombresColumnasVacias.join(', ')}</p>
      )}
    </Card>
  )
}
