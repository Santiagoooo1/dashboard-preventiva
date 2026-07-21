import { Fragment, useState } from 'react'
import type { ErrorFilaImportacionGenericaDto, TipoDato } from '../../api/types'
import type { GrupoDuplicado, RevisionClinica } from '../../utils/importacionGuiada/camposClave'
import type { ColumnaConfigurada, RolClinico } from '../../utils/importacionGuiada/sugerenciasColumnas'
import {
  ROLES_CLINICOS,
  esCampoComunDesdeRol,
  sugerirCodigoInterno,
} from '../../utils/importacionGuiada/sugerenciasColumnas'
import type { ProblemaColumna } from '../../utils/importacionGuiada/sugerenciasErrores'
import { problemaLegible } from '../../utils/importacionGuiada/sugerenciasErrores'
import styles from './ImportacionGuiada.module.css'

const TIPOS_DATO: TipoDato[] = ['TEXTO', 'ENTERO', 'DECIMAL', 'FECHA', 'BOOLEANO']
const MAX_FILAS_AFECTADAS = 20

type EstadoFila = 'sin-errores' | 'revision' | 'advertencia' | 'error' | 'sospechosa' | 'duplicada' | 'ignorada'

function estadoDe(
  columna: ColumnaConfigurada,
  problema: ProblemaColumna | undefined,
  revision: RevisionClinica | undefined,
  sospechosa: boolean,
  duplicada: boolean,
): EstadoFila {
  if (!columna.usar) return 'ignorada'
  // Sospechosa (nombre que parece un valor) manda sobre duplicada: una columna
  // "FALSE" repetida dos veces necesita corregirse por sospechosa primero.
  if (sospechosa) return 'sospechosa'
  if (duplicada) return 'duplicada'
  if (problema) return problema.estado
  if (revision) return 'revision'
  return 'sin-errores'
}

function claseFila(estado: EstadoFila): string {
  if (estado === 'error') return styles.filaError
  if (estado === 'advertencia') return styles.filaAdvertencia
  if (estado === 'revision') return styles.filaRevision
  if (estado === 'sospechosa') return styles.filaSospechosa
  if (estado === 'duplicada') return styles.filaDuplicada
  if (estado === 'ignorada') return styles.filaIgnorada
  return ''
}

interface DetectedColumnsTableProps {
  columnas: ColumnaConfigurada[]
  onChange: (columnas: ColumnaConfigurada[]) => void
  /** Problemas detectados en el último intento de validación, por índice de columna. */
  problemasPorColumna?: Map<number, ProblemaColumna>
  /** Errores de fila (sin deduplicar) por índice de columna, para "Ver filas afectadas". */
  erroresPorColumna?: Map<number, ErrorFilaImportacionGenericaDto[]>
  /** Revisiones clínicas no bloqueantes (heurísticas por nombre), por índice de columna. */
  revisionesClinicas?: Map<number, RevisionClinica>
  /** Columnas cuyo nombre parece un valor (TRUE/FALSE/0/1/vacío), por índice de columna. */
  columnasSospechosas?: Map<number, string>
  /** Columnas con el mismo nombre (exacto o normalizado) entre sí, por índice de columna. */
  columnasDuplicadas?: Map<number, GrupoDuplicado>
  /** Convierte la columna dada en la fecha principal (usado por revisiones de fecha). */
  onEstablecerFechaPrincipal?: (indiceColumna: number) => void
  /** Vuelve al paso de subir archivo, conservando el archivo ya elegido. */
  onVolverASubir?: () => void
}

export function DetectedColumnsTable({
  columnas,
  onChange,
  problemasPorColumna,
  erroresPorColumna,
  revisionesClinicas,
  columnasSospechosas,
  columnasDuplicadas,
  onEstablecerFechaPrincipal,
  onVolverASubir,
}: DetectedColumnsTableProps) {
  const [valoresVisibles, setValoresVisibles] = useState<Set<number>>(new Set())
  const [filasVisibles, setFilasVisibles] = useState<Set<number>>(new Set())

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

  const alternarFilas = (indice: number) => {
    setFilasVisibles((actual) => {
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
            const sospechosa = columnasSospechosas?.get(c.indiceColumna)
            const duplicado = columnasDuplicadas?.get(c.indiceColumna)
            const revision = !problema && !sospechosa ? revisionesClinicas?.get(c.indiceColumna) : undefined
            const estado = estadoDe(c, problema, revision, Boolean(sospechosa), Boolean(duplicado))
            const puedeAplicarSugerencia =
              problema && (problema.correccion.tipoSugerido !== null || problema.correccion.marcarNoObligatorio)
            // Bloque de detalle "crítico": identificador de paciente o fecha principal
            // con valores vacíos. No debe presentar "permitir vacío" como opción principal.
            const esBloqueCritico = Boolean(problema?.esCampoClaveCritico && problema?.esValorVacio)
            const erroresFila = erroresPorColumna?.get(c.indiceColumna) ?? []
            const esIdentificador = c.codigoInterno === 'pacienteCodigo'

            return (
              <Fragment key={c.indiceColumna}>
                <tr className={claseFila(estado)}>
                  <td>
                    {estado === 'error' && <span className={styles.etiquetaError}>Error</span>}
                    {estado === 'advertencia' && <span className={styles.etiquetaAdvertencia}>Advertencia</span>}
                    {estado === 'revision' && <span className={styles.etiquetaRevision}>Revisión recomendada</span>}
                    {estado === 'sospechosa' && <span className={styles.etiquetaSospechosa}>Columna sospechosa</span>}
                    {estado === 'duplicada' && <span className={styles.etiquetaDuplicada}>Duplicada</span>}
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
                  <td>{c.nombreOriginal || '(sin nombre)'}</td>
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

                {sospechosa && c.usar && (
                  <tr className={styles.filaDetalleProblema}>
                    <td colSpan={8}>
                      <div className={styles.detalleProblema}>
                        <span>{sospechosa}</span>
                        <div className={styles.accionesInline}>
                          <button
                            type="button"
                            className="btn btnDanger"
                            onClick={() => actualizar(c.indiceColumna, { usar: false })}
                          >
                            Ignorar columna
                          </button>
                          <button type="button" className="btn btnSecondary" onClick={onVolverASubir}>
                            Cambiar fila de cabecera
                          </button>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}

                {!sospechosa && !duplicado && problema && esBloqueCritico && (
                  <tr className={styles.filaDetalleProblema}>
                    <td colSpan={8}>
                      <div className={styles.detalleProblema}>
                        <span>{problema.problemaLegible}</span>
                        <div className={styles.accionesInline}>
                          {erroresFila.length > 0 && (
                            <button type="button" className="btn btnSecondary" onClick={() => alternarFilas(c.indiceColumna)}>
                              {filasVisibles.has(c.indiceColumna) ? 'Ocultar filas afectadas' : 'Ver filas afectadas'}
                            </button>
                          )}
                          <button type="button" className="btn btnSecondary" onClick={onVolverASubir}>
                            Corregir el archivo y volver a subirlo
                          </button>
                          <button
                            type="button"
                            className="btn btnSecondary"
                            disabled
                            title="Disponible próximamente: excluir estas filas automáticamente."
                          >
                            Excluir filas sin {esIdentificador ? 'identificador' : 'fecha principal'}
                          </button>
                        </div>
                        <p className={styles.campoClaveAyuda}>
                          Disponible próximamente: excluir estas filas automáticamente.
                        </p>
                        <p className={styles.campoClaveAyuda}>
                          {esIdentificador
                            ? 'Puedes elegir otra columna como identificador en «Campos clave del dashboard», arriba.'
                            : 'Puedes elegir otra fecha principal en «Campos clave del dashboard», arriba.'}
                        </p>

                        {filasVisibles.has(c.indiceColumna) && (
                          <div className={styles.tablaColumnasScroll}>
                            <table className={styles.tablaFilasAfectadas}>
                              <thead>
                                <tr>
                                  <th>Fila</th>
                                  <th>Columna</th>
                                  <th>Valor</th>
                                  <th>Problema</th>
                                </tr>
                              </thead>
                              <tbody>
                                {erroresFila.slice(0, MAX_FILAS_AFECTADAS).map((error, i) => (
                                  <tr key={i}>
                                    <td>{error.numeroFila ?? '—'}</td>
                                    <td>{error.nombreColumna ?? c.nombreOriginal}</td>
                                    <td>{error.valorOriginal || '(vacío)'}</td>
                                    <td>{problemaLegible(error.tipoError)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                            {erroresFila.length > MAX_FILAS_AFECTADAS && (
                              <p className={styles.campoClaveAyuda}>
                                y {erroresFila.length - MAX_FILAS_AFECTADAS} más
                              </p>
                            )}
                          </div>
                        )}

                        <details className={styles.opcionSecundaria}>
                          <summary>
                            Opción para pruebas: permitir {esIdentificador ? 'identificador' : 'fecha principal'} vacío
                            solo para esta prueba
                          </summary>
                          <button
                            type="button"
                            className="btn btnSecondary"
                            onClick={() => actualizar(c.indiceColumna, { obligatorio: false })}
                          >
                            Permitir {esIdentificador ? 'identificador' : 'fecha principal'} vacío solo para esta
                            prueba
                          </button>
                        </details>
                      </div>
                    </td>
                  </tr>
                )}

                {!sospechosa && !duplicado && problema && !esBloqueCritico && (
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

                {!sospechosa && duplicado && (
                  <tr className={styles.filaDetalleProblema}>
                    <td colSpan={8}>
                      <div className={styles.detalleProblema}>
                        <span>
                          Esta columna está repetida ({duplicado.veces} veces). Revisa el bloque «Columnas repetidas
                          detectadas», arriba.
                        </span>
                      </div>
                    </td>
                  </tr>
                )}

                {!sospechosa && !duplicado && !problema && revision && (
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
