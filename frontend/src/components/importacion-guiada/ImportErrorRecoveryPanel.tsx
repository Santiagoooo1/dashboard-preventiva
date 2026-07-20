import { Link } from 'react-router'
import type { TipoDato } from '../../api/types'
import type { ResultadoAsistente } from '../../utils/importacionGuiada/orquestador'
import type { ColumnaConfigurada } from '../../utils/importacionGuiada/sugerenciasColumnas'
import {
  agruparErroresPorColumna,
  problemaLegible,
  sugerirCorreccion,
} from '../../utils/importacionGuiada/sugerenciasErrores'
import { Card } from '../Card'
import { DataTable } from '../DataTable'
import { ImportErrorsTable } from '../importacion/ImportErrorsTable'
import styles from './ImportacionGuiada.module.css'

export interface CorreccionColumna {
  tipoDato?: TipoDato
  obligatorio?: boolean
  usar?: boolean
}

interface ImportErrorRecoveryPanelProps {
  resultado: ResultadoAsistente
  columnas: ColumnaConfigurada[]
  onCorregirColumna: (indiceColumna: number, cambios: CorreccionColumna) => void
  onReintentar: () => void
}

// El nombre de columna del error es la cabecera original del archivo.
function columnaDe(columnas: ColumnaConfigurada[], nombreColumna: string): ColumnaConfigurada | undefined {
  return columnas.find((c) => c.nombreOriginal.trim() === nombreColumna.trim())
}

export function ImportErrorRecoveryPanel({
  resultado,
  columnas,
  onCorregirColumna,
  onReintentar,
}: ImportErrorRecoveryPanelProps) {
  const errores = resultado.validacionFilas?.errores ?? []
  const grupos = agruparErroresPorColumna(errores)

  return (
    <div>
      <Card title="No se pudo importar el archivo">
        <p>
          Algunas columnas parecen tener un tipo de dato incorrecto. Puedes corregirlas y volver a validar el
          archivo.
        </p>
        {resultado.datasetId !== null && (
          <p>
            Se creó un dashboard parcial con este archivo. Puedes continuar desde él en{' '}
            <Link to={`/datasets/${resultado.datasetId}`}>modo avanzado</Link> o crear una nueva importación
            corregida.
          </p>
        )}
      </Card>

      <Card title="Columnas con problemas">
        <div className={styles.tablaRecuperacion}>
          <DataTable
            columns={[
              { key: 'nombreColumna', header: 'Columna' },
              { key: 'problema', header: 'Problema', render: (g) => problemaLegible(g.tipoErrorPredominante) },
              {
                key: 'tipoActual',
                header: 'Tipo actual',
                render: (g) => columnaDe(columnas, g.nombreColumna)?.tipoDato ?? '—',
              },
              {
                key: 'valores',
                header: 'Valores de ejemplo',
                render: (g) => (g.valoresEjemplo.length > 0 ? g.valoresEjemplo.join(', ') : '—'),
              },
              { key: 'errores', header: 'Errores', render: (g) => g.totalErrores },
              {
                key: 'sugerencia',
                header: 'Sugerencia',
                render: (g) => sugerirCorreccion(g, columnaDe(columnas, g.nombreColumna)).texto,
              },
              {
                key: 'accion',
                header: 'Acción',
                render: (g) => {
                  const columna = columnaDe(columnas, g.nombreColumna)
                  if (!columna) return <span>Revisar manualmente</span>
                  const sugerencia = sugerirCorreccion(g, columna)
                  const aplicarSugerencia = () => {
                    if (sugerencia.tipoSugerido) {
                      onCorregirColumna(columna.indiceColumna, { tipoDato: sugerencia.tipoSugerido })
                    }
                    if (sugerencia.marcarNoObligatorio) {
                      onCorregirColumna(columna.indiceColumna, { obligatorio: false })
                    }
                  }
                  const puedeAplicar = sugerencia.tipoSugerido !== null || sugerencia.marcarNoObligatorio
                  return (
                    <div className="rowActions">
                      {puedeAplicar && (
                        <button type="button" className="btn btnAction" onClick={aplicarSugerencia}>
                          Aplicar sugerencia
                        </button>
                      )}
                      <select
                        className={styles.selectAccion}
                        value=""
                        onChange={(e) => {
                          const v = e.target.value
                          if (v === 'TEXTO' || v === 'BOOLEANO' || v === 'ENTERO' || v === 'DECIMAL') {
                            onCorregirColumna(columna.indiceColumna, { tipoDato: v })
                          } else if (v === 'no-obligatorio') {
                            onCorregirColumna(columna.indiceColumna, { obligatorio: false })
                          } else if (v === 'ignorar') {
                            onCorregirColumna(columna.indiceColumna, { usar: false })
                          }
                          e.target.value = ''
                        }}
                      >
                        <option value="">Otra acción…</option>
                        <option value="TEXTO">Cambiar a Texto</option>
                        <option value="BOOLEANO">Cambiar a Sí/No</option>
                        <option value="ENTERO">Cambiar a Número entero</option>
                        <option value="DECIMAL">Cambiar a Número decimal</option>
                        <option value="no-obligatorio">Marcar como no obligatorio</option>
                        <option value="ignorar">Ignorar columna</option>
                      </select>
                    </div>
                  )
                },
              },
            ]}
            rows={grupos}
            getRowKey={(g) => g.nombreColumna}
          />
        </div>
        <div className={styles.acciones}>
          <button type="button" className="btn btnPrimary" onClick={onReintentar}>
            Corregir columnas y reintentar
          </button>
        </div>
      </Card>

      {errores.length > 0 && (
        <details className={styles.detalleErrores}>
          <summary>Mostrando los primeros 100 errores técnicos</summary>
          <div style={{ marginTop: 'var(--spacing-sm)' }}>
            <ImportErrorsTable errores={errores} />
          </div>
        </details>
      )}
    </div>
  )
}
