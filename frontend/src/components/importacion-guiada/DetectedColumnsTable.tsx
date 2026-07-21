import { Fragment, useState } from 'react'
import type { TipoDato } from '../../api/types'
import type { RevisionClinica } from '../../utils/importacionGuiada/camposClave'
import type { ColumnaConfigurada, RolClinico } from '../../utils/importacionGuiada/sugerenciasColumnas'
import {
  ROLES_CLINICOS,
  esCampoComunDesdeRol,
  sugerirCodigoInterno,
} from '../../utils/importacionGuiada/sugerenciasColumnas'
import type { ProblemaColumna } from '../../utils/importacionGuiada/sugerenciasErrores'
import styles from './ImportacionGuiada.module.css'

const TIPOS_DATO: TipoDato[] = ['TEXTO', 'ENTERO', 'DECIMAL', 'FECHA', 'BOOLEANO']

type EstadoFila = 'sin-errores' | 'revision' | 'advertencia' | 'error' | 'ignorada'

function estadoDe(
  columna: ColumnaConfigurada,
  problema: ProblemaColumna | undefined,
  revision: RevisionClinica | undefined,
): EstadoFila {
  if (!columna.usar) return 'ignorada'
  if (problema) return problema.estado
  if (revision) return 'revision'
  return 'sin-errores'
}

function claseFila(estado: EstadoFila): string {
  if (estado === 'error') return styles.filaError
  if (estado === 'advertencia') return styles.filaAdvertencia
  if (estado === 'revision') return styles.filaRevision
  if (estado === 'ignorada') return styles.filaIgnorada
  return ''
}

interface DetectedColumnsTableProps {
  columnas: ColumnaConfigurada[]
  onChange: (columnas: ColumnaConfigurada[]) => void
  /** Problemas detectados en el último intento de validación, por índice de columna. */
  problemasPorColumna?: Map<number, ProblemaColumna>
  /** Revisiones clínicas no bloqueantes (heurísticas por nombre), por índice de columna. */
  revisionesClinicas?: Map<number, RevisionClinica>
  /** Convierte la columna dada en la fecha principal (usado por revisiones de fecha). */
  onEstablecerFechaPrincipal?: (indiceColumna: number) => void
}

export function DetectedColumnsTable({
  columnas,
  onChange,
  problemasPorColumna,
  revisionesClinicas,
  onEstablecerFechaPrincipal,
}: DetectedColumnsTableProps) {
  const [valoresVisibles, setValoresVisibles] = useState<Set<number>>(new Set())

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

  const aplicarSugerencia = (columna: ColumnaConfigurada, problema: ProblemaColumna) => {
    const cambios: Partial<ColumnaConfigurada> = {}
    if (problema.correccion.tipoSugerido) cambios.tipoDato = problema.correccion.tipoSugerido
    if (problema.correccion.marcarNoObligatorio) cambios.obligatorio = false
    if (Object.keys(cambios).length > 0) actualizar(columna.indiceColumna, cambios)
  }

  const aplicarRevision = (columna: ColumnaConfigurada, revision: RevisionClinica) => {
    if (revision.indiceFechaSugerida !== undefined) {
      onEstablecerFechaPrincipal?.(revision.indiceFechaSugerida)
      return
    }
    if (revision.tipoSugerido) actualizar(columna.indiceColumna, { tipoDato: revision.tipoSugerido })
  }

  const alternarValores = (indice: number) => {
    setValoresVisibles((actual) => {
      const siguiente = new Set(actual)
      if (siguiente.has(indice)) siguiente.delete(indice)
      else siguiente.add(indice)
      return siguiente
    })
  }

  return (
    <div className={styles.tablaColumnasScroll}>
      <table className={styles.tablaColumnasNativa}>
        <thead>
          <tr>
            <th>Estado</th>
            <th>Usar</th>
            <th>Columna del archivo</th>
            <th>Nombre visible</th>
            <th>Identificador</th>
            <th>Tipo de dato</th>
            <th>Rol clínico</th>
            <th>Obligatorio</th>
          </tr>
        </thead>
        <tbody>
          {columnas.map((c) => {
            const problema = problemasPorColumna?.get(c.indiceColumna)
            const revision = !problema ? revisionesClinicas?.get(c.indiceColumna) : undefined
            const estado = estadoDe(c, problema, revision)
            const puedeAplicarSugerencia =
              problema && (problema.correccion.tipoSugerido !== null || problema.correccion.marcarNoObligatorio)

            return (
              <Fragment key={c.indiceColumna}>
                <tr className={claseFila(estado)}>
                  <td>
                    {estado === 'error' && <span className={styles.etiquetaError}>Error</span>}
                    {estado === 'advertencia' && <span className={styles.etiquetaAdvertencia}>Advertencia</span>}
                    {estado === 'revision' && <span className={styles.etiquetaRevision}>Revisión recomendada</span>}
                    {estado === 'ignorada' && <span className={styles.etiquetaIgnorada}>Ignorada</span>}
                    {estado === 'sin-errores' && <span className={styles.etiquetaSinErrores}>Sin errores</span>}
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      checked={c.usar}
                      onChange={(e) => actualizar(c.indiceColumna, { usar: e.target.checked })}
                      aria-label={`Usar la columna ${c.nombreOriginal}`}
                    />
                  </td>
                  <td>{c.nombreOriginal}</td>
                  <td>
                    <input
                      type="text"
                      value={c.nombreVisible}
                      disabled={!c.usar}
                      onChange={(e) => actualizar(c.indiceColumna, { nombreVisible: e.target.value })}
                    />
                  </td>
                  <td>
                    <input
                      type="text"
                      value={c.codigoInterno}
                      disabled={!c.usar}
                      onChange={(e) =>
                        actualizar(c.indiceColumna, { codigoInterno: sugerirCodigoInterno(e.target.value) })
                      }
                    />
                  </td>
                  <td>
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
                  </td>
                  <td>
                    <select value={c.rol} onChange={(e) => cambiarRol(c, e.target.value as RolClinico)}>
                      {ROLES_CLINICOS.map((r) => (
                        <option key={r.valor} value={r.valor}>
                          {r.etiqueta}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      checked={c.obligatorio}
                      disabled={!c.usar}
                      onChange={(e) => actualizar(c.indiceColumna, { obligatorio: e.target.checked })}
                      aria-label={`Marcar ${c.nombreOriginal} como obligatoria`}
                    />
                  </td>
                </tr>

                {problema && (
                  <tr className={styles.filaDetalleProblema}>
                    <td colSpan={8}>
                      <div className={styles.detalleProblema}>
                        <span>{problema.problemaLegible}</span>
                        {problema.valoresEjemplo.length > 0 && (
                          <button
                            type="button"
                            className="btn btnSecondary"
                            onClick={() => alternarValores(c.indiceColumna)}
                          >
                            {valoresVisibles.has(c.indiceColumna) ? 'Ocultar valores' : 'Ver valores afectados'}
                          </button>
                        )}
                        {valoresVisibles.has(c.indiceColumna) && (
                          <span className={styles.valoresEjemplo}>
                            Valores detectados: {problema.valoresEjemplo.join(', ')}
                          </span>
                        )}
                        <span>Sugerencia: {problema.correccion.texto}.</span>
                        {problema.esCampoClaveCritico && (
                          <span className={styles.valoresEjemplo}>
                            Puedes elegir otra columna en «Campos clave del dashboard», arriba.
                          </span>
                        )}
                        <div className={styles.accionesInline}>
                          {puedeAplicarSugerencia && (
                            <button
                              type="button"
                              className="btn btnAction"
                              onClick={() => aplicarSugerencia(c, problema)}
                            >
                              Aplicar sugerencia
                            </button>
                          )}
                          <button
                            type="button"
                            className="btn btnSecondary"
                            onClick={() => actualizar(c.indiceColumna, { obligatorio: false })}
                          >
                            Marcar como no obligatoria
                          </button>
                          <button
                            type="button"
                            className="btn btnDanger"
                            onClick={() => actualizar(c.indiceColumna, { usar: false })}
                          >
                            Ignorar columna
                          </button>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}

                {!problema && revision && (
                  <tr className={styles.filaDetalleProblema}>
                    <td colSpan={8}>
                      <div className={styles.detalleProblema}>
                        <span>{revision.mensaje}</span>
                        {(revision.tipoSugerido || revision.indiceFechaSugerida !== undefined) && (
                          <div className={styles.accionesInline}>
                            <button type="button" className="btn btnAction" onClick={() => aplicarRevision(c, revision)}>
                              Aplicar sugerencia
                            </button>
                          </div>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
