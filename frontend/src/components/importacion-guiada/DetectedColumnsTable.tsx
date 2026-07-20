import type { TipoDato } from '../../api/types'
import type { ColumnaConfigurada, RolClinico } from '../../utils/importacionGuiada/sugerenciasColumnas'
import {
  ROLES_CLINICOS,
  esCampoComunDesdeRol,
  sugerirCodigoInterno,
} from '../../utils/importacionGuiada/sugerenciasColumnas'
import { DataTable } from '../DataTable'
import styles from './ImportacionGuiada.module.css'

const TIPOS_DATO: TipoDato[] = ['TEXTO', 'ENTERO', 'DECIMAL', 'FECHA', 'BOOLEANO']

interface DetectedColumnsTableProps {
  columnas: ColumnaConfigurada[]
  onChange: (columnas: ColumnaConfigurada[]) => void
}

export function DetectedColumnsTable({ columnas, onChange }: DetectedColumnsTableProps) {
  const actualizar = (indice: number, cambios: Partial<ColumnaConfigurada>) => {
    onChange(columnas.map((c) => (c.indiceColumna === indice ? { ...c, ...cambios } : c)))
  }

  // Al cambiar el rol se re-deriva el "campo común" internamente (nunca visible).
  const cambiarRol = (columna: ColumnaConfigurada, rol: RolClinico) => {
    actualizar(columna.indiceColumna, {
      rol,
      esComun: rol === 'fecha' ? columna.codigoInterno === 'fechaEvento' : esCampoComunDesdeRol(rol),
      usar: rol === 'ignorar' ? false : columna.usar,
    })
  }

  return (
    <div className={styles.tablaColumnas}>
      <DataTable
        columns={[
          {
            key: 'usar',
            header: 'Usar',
            render: (c) => (
              <input
                type="checkbox"
                checked={c.usar}
                onChange={(e) => actualizar(c.indiceColumna, { usar: e.target.checked })}
                aria-label={`Usar la columna ${c.nombreOriginal}`}
              />
            ),
          },
          { key: 'nombreOriginal', header: 'Columna del archivo' },
          {
            key: 'nombreVisible',
            header: 'Nombre visible',
            render: (c) => (
              <input
                type="text"
                value={c.nombreVisible}
                disabled={!c.usar}
                onChange={(e) => {
                  const nombreVisible = e.target.value
                  // Si el código no se ha tocado a mano, lo mantenemos alineado.
                  actualizar(c.indiceColumna, { nombreVisible })
                }}
              />
            ),
          },
          {
            key: 'codigoInterno',
            header: 'Identificador',
            render: (c) => (
              <input
                type="text"
                value={c.codigoInterno}
                disabled={!c.usar}
                onChange={(e) => actualizar(c.indiceColumna, { codigoInterno: sugerirCodigoInterno(e.target.value) })}
              />
            ),
          },
          {
            key: 'tipoDato',
            header: 'Tipo de dato',
            render: (c) => (
              <select
                value={c.tipoDato}
                disabled={!c.usar}
                onChange={(e) => actualizar(c.indiceColumna, { tipoDato: e.target.value as TipoDato })}
              >
                {TIPOS_DATO.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            ),
          },
          {
            key: 'rol',
            header: 'Rol clínico',
            render: (c) => (
              <select value={c.rol} onChange={(e) => cambiarRol(c, e.target.value as RolClinico)}>
                {ROLES_CLINICOS.map((r) => (
                  <option key={r.valor} value={r.valor}>
                    {r.etiqueta}
                  </option>
                ))}
              </select>
            ),
          },
          {
            key: 'obligatorio',
            header: 'Obligatorio',
            render: (c) => (
              <input
                type="checkbox"
                checked={c.obligatorio}
                disabled={!c.usar}
                onChange={(e) => actualizar(c.indiceColumna, { obligatorio: e.target.checked })}
                aria-label={`Marcar ${c.nombreOriginal} como obligatoria`}
              />
            ),
          },
        ]}
        rows={columnas}
        getRowKey={(c) => c.indiceColumna}
      />
    </div>
  )
}
